package com.example.edgedevicedemo.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EdgeChatApp(
    viewModel: EdgeChatViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModel(uri)
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppHeader(
                currentTab = state.currentTab,
                onOpenSettings = { viewModel.selectTab(AppTab.Settings) },
                onBack = { viewModel.selectTab(AppTab.Chat) }
            )
        }
    ) { innerPadding ->
        when (state.currentTab) {
            AppTab.Chat -> ChatScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = state,
                listState = listState,
                onDraftChanged = viewModel::updateDraftMessage,
                onSend = viewModel::sendMessage
            )

            AppTab.Settings -> SettingsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = state,
                onProviderModeSelected = viewModel::updateProviderMode,
                onLocalBackendSelected = viewModel::updateLocalBackend,
                onImportModel = { importLauncher.launch(arrayOf("*/*")) },
                onCloudModelNameChanged = viewModel::updateCloudModelName,
                onCloudApiKeyChanged = viewModel::updateCloudApiKey,
                onAllowCloudFallbackChanged = viewModel::updateAllowCloudFallback,
                onStayOfflineChanged = viewModel::updateStayOfflineWhenLocal
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppHeader(
    currentTab: AppTab,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(if (currentTab == AppTab.Chat) "Edge Chat" else "Settings")
        },
        navigationIcon = {
            if (currentTab == AppTab.Settings) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (currentTab == AppTab.Chat) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    )
}

@Composable
private fun ChatScreen(
    modifier: Modifier,
    state: AppUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            items(items = state.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = state.draftMessage,
                    onValueChange = onDraftChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    placeholder = { Text("Type a message...") },
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = if (state.isSending) "Generating..." else "Ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onSend,
                        enabled = state.draftMessage.isNotBlank() && !state.isSending,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: AppUiState,
    onProviderModeSelected: (ProviderMode) -> Unit,
    onLocalBackendSelected: (LocalBackend) -> Unit,
    onImportModel: () -> Unit,
    onCloudModelNameChanged: (String) -> Unit,
    onCloudApiKeyChanged: (String) -> Unit,
    onAllowCloudFallbackChanged: (Boolean) -> Unit,
    onStayOfflineChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.widthIn(max = 720.dp)) {
            SettingsSection(title = "AI mode", subtitle = "Choose how each reply should route.") {
                ChipRow {
                    ProviderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.providerMode == mode,
                            onClick = { onProviderModeSelected(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            SettingsSection(title = "Local model", subtitle = "Import the .litertlm file from phone storage.") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = settings.localModelName.ifBlank { "No model imported" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = settings.localModelSizeBytes?.let {
                                    Formatter.formatShortFileSize(context, it)
                                } ?: "Importing copies the selected model into app storage so LiteRT-LM can open it by file path.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.localStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ChipRow {
                        LocalBackend.entries.forEach { backend ->
                            FilterChip(
                                selected = settings.localBackend == backend,
                                onClick = { onLocalBackendSelected(backend) },
                                label = { Text(backend.label) }
                            )
                        }
                    }

                    Button(onClick = onImportModel, shape = RoundedCornerShape(8.dp)) {
                        Text(if (settings.localModelPath == null) "Import model" else "Replace model")
                    }
                }
            }

            SettingsSection(title = "Gemini cloud", subtitle = "Optional cloud route for messages that should leave the device.") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = settings.cloudModelName,
                        onValueChange = onCloudModelNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cloud model") },
                        placeholder = { Text("gemini-2.5-flash-lite") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settings.cloudApiKey,
                        onValueChange = onCloudApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API key") },
                        placeholder = { Text("Paste your key") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }

            SettingsSection(title = "Behavior", subtitle = "Keep local private and cloud explicit.") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleRow(
                        title = "Stay offline in local mode",
                        subtitle = "When Local is selected, never fall through to cloud.",
                        checked = settings.stayOfflineWhenLocal,
                        onCheckedChange = onStayOfflineChanged
                    )
                    ToggleRow(
                        title = "Allow cloud fallback in auto mode",
                        subtitle = "If the local model is unavailable, Auto can use Gemini.",
                        checked = settings.allowCloudFallback,
                        onCheckedChange = onAllowCloudFallbackChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ChatBubble(message: UiChatMessage) {
    val isUser = message.role == ChatRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(8.dp),
            color = when {
                message.isError -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isUser) "You" else "Edge Chat",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
