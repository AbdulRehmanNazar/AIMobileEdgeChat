package com.example.edgedevicedemo.chat

import android.app.Application
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.example.edgedevicedemo.shared.model.AppSettings
import com.example.edgedevicedemo.shared.model.LocalBackend
import com.example.edgedevicedemo.shared.platform.InitializationResult
import com.example.edgedevicedemo.shared.platform.LocalCapabilities
import com.example.edgedevicedemo.shared.platform.LocalChatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalModelManager(
    private val application: Application
) : LocalChatService {
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelPath: String? = null
    private var activeBackend: LocalBackend? = null

    override val capabilities = LocalCapabilities(
        available = true,
        importEnabled = true
    )

    override suspend fun initialize(settings: AppSettings): InitializationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val modelPath = requireNotNull(settings.localModelPath) {
                "Import a .litertlm model first."
            }

            if (engine != null &&
                conversation != null &&
                activeModelPath == modelPath &&
                activeBackend == settings.localBackend
            ) {
                return@withLock InitializationResult(settings.localBackend, null)
            }

            closeLocked()

            val backendOrder = when (settings.localBackend) {
                LocalBackend.Cpu -> listOf(LocalBackend.Cpu)
                // LocalBackend.Gpu -> listOf(LocalBackend.Gpu, LocalBackend.Cpu)
            }

            var lastError: Throwable? = null
            backendOrder.forEachIndexed { index, backendChoice ->
                try {
                    Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                    val candidateEngine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backendChoice.toRuntimeBackend(),
                            cacheDir = application.cacheDir.absolutePath
                        )
                    )
                    candidateEngine.initialize()
                    val candidateConversation = candidateEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(SYSTEM_PROMPT)
                        )
                    )

                    engine = candidateEngine
                    conversation = candidateConversation
                    activeModelPath = modelPath
                    activeBackend = backendChoice

                    val fallbackFrom = if (index == 0) null else settings.localBackend
                    return@withLock InitializationResult(backendChoice, fallbackFrom)
                } catch (throwable: Throwable) {
                    lastError = throwable
                }
            }

            throw lastError ?: IllegalStateException("Model initialization failed.")
        }
    }

    override suspend fun resetConversation() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentEngine = engine ?: return@withLock
            conversation?.close()
            conversation = currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM_PROMPT)
                )
            )
        }
    }

    override suspend fun sendMessage(
        prompt: String,
        onChunk: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentConversation = requireNotNull(conversation) {
                "The local model is not ready yet."
            }

            currentConversation.sendMessageAsync(prompt).collect { message ->
                val text = message.toString()
                if (text.isNotBlank()) {
                    onChunk(text)
                }
            }
        }
    }

    override fun close() {
        runCatching {
            synchronized(this) {
                closeLocked()
            }
        }
    }

    private fun closeLocked() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        activeModelPath = null
        activeBackend = null
    }

    private fun LocalBackend.toRuntimeBackend(): Backend {
        return when (this) {
            // LocalBackend.Gpu -> Backend.GPU()
            LocalBackend.Cpu -> Backend.CPU()
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful mobile assistant running on-device. Keep replies concise and useful."
    }
}
