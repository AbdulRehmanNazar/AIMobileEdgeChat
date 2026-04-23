package com.example.edgedevicedemo.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.edgedevicedemo.chat.GeminiCloudClient
import com.example.edgedevicedemo.chat.LocalModelManager
import com.example.edgedevicedemo.data.AppPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EdgeChatViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val localModelManager = LocalModelManager(application)
    private val geminiCloudClient = GeminiCloudClient()
    private val messageIds = AtomicLong(1)

    private val _uiState = MutableStateFlow(
        AppUiState(settings = sanitizeSettings(preferences.load()))
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        val currentSettings = _uiState.value.settings
        if (currentSettings.localModelPath != null) {
            viewModelScope.launch {
                initializeLocalRuntime()
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateDraftMessage(value: String) {
        _uiState.update { it.copy(draftMessage = value) }
    }

    fun updateProviderMode(mode: ProviderMode) {
        updateSettings { copy(providerMode = mode) }
        if (mode != ProviderMode.Cloud && _uiState.value.settings.localModelPath != null) {
            viewModelScope.launch {
                initializeLocalRuntime()
            }
        }
    }

    fun updateLocalBackend(backend: LocalBackend) {
        updateSettings { copy(localBackend = backend) }
        if (_uiState.value.settings.localModelPath != null) {
            viewModelScope.launch {
                initializeLocalRuntime()
            }
        }
    }

    fun updateCloudModelName(modelName: String) {
        updateSettings { copy(cloudModelName = modelName) }
    }

    fun updateCloudApiKey(apiKey: String) {
        updateSettings { copy(cloudApiKey = apiKey) }
    }

    fun updateAllowCloudFallback(enabled: Boolean) {
        updateSettings { copy(allowCloudFallback = enabled) }
    }

    fun updateStayOfflineWhenLocal(enabled: Boolean) {
        updateSettings { copy(stayOfflineWhenLocal = enabled) }
    }

    fun startNewChat() {
        _uiState.update {
            it.copy(
                messages = DEFAULT_MESSAGES,
                draftMessage = "",
                isSending = false
            )
        }
        viewModelScope.launch {
            runCatching { localModelManager.resetConversation() }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val application = getApplication<Application>()
            _uiState.update {
                it.copy(
                    localRuntimeState = LocalRuntimeState.Importing,
                    localStatusMessage = "Copying the model into app storage..."
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    val contentResolver = application.contentResolver
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                    }

                    val imported = contentResolver.importModelFile(uri, application.filesDir)
                    updateSettings {
                        copy(
                            providerMode = if (providerMode == ProviderMode.Cloud) {
                                ProviderMode.Auto
                            } else {
                                providerMode
                            },
                            localModelPath = imported.path,
                            localModelName = imported.displayName,
                            localModelSizeBytes = imported.sizeBytes
                        )
                    }
                }
            }.onSuccess {
                initializeLocalRuntime()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        localRuntimeState = LocalRuntimeState.Failed,
                        localStatusMessage = throwable.message ?: "Model import failed."
                    )
                }
            }
        }
    }

    fun sendMessage() {
        val draft = _uiState.value.draftMessage.trim()
        if (draft.isEmpty() || _uiState.value.isSending) return

        val userMessage = UiChatMessage(
            id = messageIds.getAndIncrement(),
            role = ChatRole.User,
            text = draft
        )
        val assistantMessageId = messageIds.getAndIncrement()
        val assistantPlaceholder = UiChatMessage(
            id = assistantMessageId,
            role = ChatRole.Assistant,
            text = "",
            isStreaming = true
        )

        val snapshot = _uiState.value
        val cloudTurns = buildCloudTurns(snapshot.messages + userMessage)

        _uiState.update {
            it.copy(
                draftMessage = "",
                isSending = true,
                messages = it.messages + userMessage + assistantPlaceholder
            )
        }

        viewModelScope.launch {
            runCatching {
                when (resolveRoute(_uiState.value.settings)) {
                    ProviderMode.Local -> {
                        ensureLocalReady(allowFallback = false)
                        sendLocal(draft, assistantMessageId)
                    }

                    ProviderMode.Cloud -> {
                        sendCloud(cloudTurns, assistantMessageId)
                    }

                    ProviderMode.Auto -> {
                        val localReady = ensureLocalReady(
                            allowFallback = _uiState.value.settings.allowCloudFallback
                        )
                        if (localReady) {
                            runCatching {
                                sendLocal(draft, assistantMessageId)
                            }.onFailure { localFailure ->
                                if (canUseCloudFallback(_uiState.value.settings)) {
                                    overwriteAssistantMessage(
                                        assistantMessageId,
                                        "Local model failed, switching to Gemini..."
                                    )
                                    sendCloud(cloudTurns, assistantMessageId, resetText = true)
                                } else {
                                    throw localFailure
                                }
                            }
                        } else {
                            sendCloud(cloudTurns, assistantMessageId)
                        }
                    }
                }
            }.onSuccess {
                finishAssistantMessage(assistantMessageId)
            }.onFailure { throwable ->
                failAssistantMessage(assistantMessageId, throwable)
            }

            _uiState.update { it.copy(isSending = false) }
        }
    }

    private suspend fun sendLocal(
        prompt: String,
        assistantMessageId: Long
    ) {
        localModelManager.sendMessage(prompt) { chunk ->
            appendAssistantChunk(assistantMessageId, chunk)
        }
    }

    private suspend fun sendCloud(
        turns: List<CloudTurn>,
        assistantMessageId: Long,
        resetText: Boolean = false
    ) {
        if (resetText) {
            overwriteAssistantMessage(assistantMessageId, "")
        }

        val settings = _uiState.value.settings
        val response = geminiCloudClient.generateReply(
            apiKey = settings.cloudApiKey,
            modelName = settings.cloudModelName,
            turns = turns
        )
        overwriteAssistantMessage(assistantMessageId, response)
    }

    private suspend fun ensureLocalReady(allowFallback: Boolean): Boolean {
        val settings = _uiState.value.settings
        if (settings.localModelPath.isNullOrBlank()) {
            if (allowFallback) return false
            error("Import a .litertlm model to use local chat.")
        }

        return runCatching {
            initializeLocalRuntime()
            true
        }.getOrElse { throwable ->
            if (allowFallback) {
                false
            } else {
                throw throwable
            }
        }
    }

    private suspend fun initializeLocalRuntime() {
        val settings = _uiState.value.settings
        val modelName = settings.localModelName.ifBlank { "Local model" }

        _uiState.update {
            it.copy(
                localRuntimeState = LocalRuntimeState.Initializing,
                localStatusMessage = "Loading $modelName..."
            )
        }

        runCatching {
            localModelManager.initialize(settings)
        }.onSuccess { result ->
            val statusMessage = result.fallbackFrom?.let {
                "$modelName is running on ${result.backend.label}. ${it.label} was unavailable on this device."
            } ?: "$modelName is ready on ${result.backend.label}."

            _uiState.update {
                it.copy(
                    localRuntimeState = LocalRuntimeState.Ready,
                    localStatusMessage = statusMessage,
                    activeBackend = result.backend
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    localRuntimeState = LocalRuntimeState.Failed,
                    localStatusMessage = throwable.message ?: "Local model failed to initialize.",
                    activeBackend = null
                )
            }
            throw throwable
        }
    }

    private fun resolveRoute(settings: AppSettings): ProviderMode {
        return when (settings.providerMode) {
            ProviderMode.Local -> ProviderMode.Local
            ProviderMode.Cloud -> ProviderMode.Cloud
            ProviderMode.Auto -> {
                if (!settings.localModelPath.isNullOrBlank()) {
                    ProviderMode.Auto
                } else if (settings.cloudApiKey.isNotBlank()) {
                    ProviderMode.Cloud
                } else {
                    ProviderMode.Local
                }
            }
        }
    }

    private fun canUseCloudFallback(settings: AppSettings): Boolean {
        return settings.allowCloudFallback && settings.cloudApiKey.isNotBlank()
    }

    private fun buildCloudTurns(messages: List<UiChatMessage>): List<CloudTurn> {
        return messages
            .filter { it.id != 0L }
            .filter { !it.isError }
            .filter { it.text.isNotBlank() }
            .map { CloudTurn(role = it.role, text = it.text) }
    }

    private fun appendAssistantChunk(messageId: Long, chunk: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(text = message.text + chunk, isStreaming = true)
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun overwriteAssistantMessage(messageId: Long, text: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(text = text, isStreaming = true, isError = false)
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun finishAssistantMessage(messageId: Long) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(
                            text = message.text.ifBlank { "Done." },
                            isStreaming = false,
                            isError = false
                        )
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun failAssistantMessage(messageId: Long, throwable: Throwable) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(
                            text = throwable.message ?: "That request failed.",
                            isStreaming = false,
                            isError = true
                        )
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun updateSettings(transform: AppSettings.() -> AppSettings) {
        val updatedSettings = _uiState.value.settings.transform().let(::sanitizeSettings)
        preferences.save(updatedSettings)
        _uiState.update { it.copy(settings = updatedSettings) }
    }

    private fun sanitizeSettings(settings: AppSettings): AppSettings {
        val localPath = settings.localModelPath?.takeIf { File(it).exists() }
        return if (localPath == settings.localModelPath) {
            settings
        } else {
            settings.copy(
                localModelPath = null,
                localModelName = "",
                localModelSizeBytes = null
            ).also(preferences::save)
        }
    }

    override fun onCleared() {
        localModelManager.close()
        super.onCleared()
    }

    private data class ImportedModel(
        val path: String,
        val displayName: String,
        val sizeBytes: Long
    )

    private fun android.content.ContentResolver.importModelFile(
        uri: Uri,
        appFilesDir: File
    ): ImportedModel {
        val displayName = queryDisplayName(uri)
        val modelDirectory = File(appFilesDir, "models").apply { mkdirs() }
        val targetFile = File(modelDirectory, sanitizeFileName(displayName))

        openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open the selected model file.")

        return ImportedModel(
            path = targetFile.absolutePath,
            displayName = displayName,
            sizeBytes = targetFile.length()
        )
    }

    private fun android.content.ContentResolver.queryDisplayName(uri: Uri): String {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(columnIndex) ?: DEFAULT_MODEL_FILE_NAME
            }
        }

        return uri.lastPathSegment ?: DEFAULT_MODEL_FILE_NAME
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    companion object {
        private const val DEFAULT_MODEL_FILE_NAME = "model.litertlm"

        private val DEFAULT_MESSAGES = listOf(
            UiChatMessage(
                id = 0,
                role = ChatRole.Assistant,
                text = "Import your Gemma 4 E2B LiteRT-LM file, or add a Gemini API key for cloud chat."
            )
        )
    }
}
