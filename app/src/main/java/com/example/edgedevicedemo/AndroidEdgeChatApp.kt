package com.example.edgedevicedemo

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.edgedevicedemo.chat.LocalModelManager
import com.example.edgedevicedemo.data.AppPreferences
import com.example.edgedevicedemo.shared.chat.EdgeChatController
import com.example.edgedevicedemo.shared.chat.MultiCloudChatClient
import com.example.edgedevicedemo.shared.model.AppTab
import com.example.edgedevicedemo.shared.model.ImportedLocalModel
import com.example.edgedevicedemo.shared.ui.EdgeChatApp
import com.example.edgedevicedemo.shared.ui.EdgeChatTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AndroidEdgeChatApp() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val settingsStore = remember(application) { AppPreferences(application) }
    val localChatService = remember(application) { LocalModelManager(application) }
    val cloudChatClient = remember { MultiCloudChatClient() }
    val controller = remember(settingsStore, localChatService, cloudChatClient) {
        EdgeChatController(
            settingsStore = settingsStore,
            localChatService = localChatService,
            cloudChatClient = cloudChatClient
        )
    }
    val state by controller.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    BackHandler(enabled = state.currentTab == AppTab.Settings) {
        controller.selectTab(AppTab.Chat)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Show "Copying…" spinner immediately — before the IO copy starts.
            controller.onLocalModelImportStarted()
            coroutineScope.launch {
                runCatching {
                    application.contentResolver.importModelFile(uri, application.filesDir)
                }.onSuccess(controller::onLocalModelImported)
                    .onFailure { throwable ->
                        controller.onLocalModelImportFailed(
                            throwable.message ?: "Model import failed."
                        )
                    }
            }
        }
    }

    EdgeChatTheme {
        EdgeChatApp(
            controller = controller,
            onImportModel = { importLauncher.launch(arrayOf("*/*")) },
            formatBytes = { Formatter.formatShortFileSize(context, it) }
        )
    }
}

private suspend fun android.content.ContentResolver.importModelFile(
    uri: Uri,
    appFilesDir: File
): ImportedLocalModel = withContext(Dispatchers.IO) {
    try {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
    }

    val displayName = queryDisplayName(uri)
    val modelDirectory = File(appFilesDir, "models").apply { mkdirs() }
    val targetFile = File(modelDirectory, sanitizeFileName(displayName))

    // Skip the copy if the exact same file is already present in app storage
    // (happens when the user picks the same model a second time).
    if (!targetFile.exists() || targetFile.length() == 0L) {
        openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open the selected model file.")
    }

    ImportedLocalModel(
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

private const val DEFAULT_MODEL_FILE_NAME = "model.litertlm"
