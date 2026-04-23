package com.example.edgedevicedemo.ui

enum class AppTab(val label: String) {
    Chat("Chat"),
    Settings("Settings")
}

enum class ProviderMode(val label: String) {
    Local("Local"),
    Cloud("Cloud"),
    Auto("Auto")
}

enum class LocalBackend(val label: String) {
    Gpu("GPU"),
    Cpu("CPU")
}

enum class ChatRole {
    User,
    Assistant
}

enum class LocalRuntimeState {
    NotConfigured,
    Importing,
    Initializing,
    Ready,
    Failed
}

data class AppSettings(
    val providerMode: ProviderMode = ProviderMode.Local,
    val localModelPath: String? = null,
    val localModelName: String = "",
    val localModelSizeBytes: Long? = null,
    val localBackend: LocalBackend = LocalBackend.Cpu,
    val cloudModelName: String = "gemini-2.5-flash-lite",
    val cloudApiKey: String = "",
    val allowCloudFallback: Boolean = true,
    val stayOfflineWhenLocal: Boolean = true
)

data class UiChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class AppUiState(
    val currentTab: AppTab = AppTab.Chat,
    val settings: AppSettings = AppSettings(),
    val messages: List<UiChatMessage> = listOf(
        UiChatMessage(
            id = 0,
            role = ChatRole.Assistant,
            text = "Import your Gemma 4 E2B LiteRT-LM file, or add a Gemini API key for cloud chat."
        )
    ),
    val draftMessage: String = "",
    val isSending: Boolean = false,
    val localRuntimeState: LocalRuntimeState = LocalRuntimeState.NotConfigured,
    val localStatusMessage: String = "Import a .litertlm model to start on-device chat.",
    val activeBackend: LocalBackend? = null
)

data class CloudTurn(
    val role: ChatRole,
    val text: String
)
