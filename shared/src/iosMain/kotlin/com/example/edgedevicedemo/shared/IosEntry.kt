package com.example.edgedevicedemo.shared

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.edgedevicedemo.shared.chat.EdgeChatController
import com.example.edgedevicedemo.shared.chat.MultiCloudChatClient
import com.example.edgedevicedemo.shared.model.AppSettings
import com.example.edgedevicedemo.shared.model.CloudProvider
import com.example.edgedevicedemo.shared.model.LocalBackend
import com.example.edgedevicedemo.shared.model.ProviderMode
import com.example.edgedevicedemo.shared.platform.SettingsStore
import com.example.edgedevicedemo.shared.platform.UnsupportedLocalChatService
import com.example.edgedevicedemo.shared.ui.EdgeChatApp
import com.example.edgedevicedemo.shared.ui.EdgeChatTheme
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val controller = remember {
        EdgeChatController(
            settingsStore = IosSettingsStore(),
            localChatService = UnsupportedLocalChatService(
                "On-device Gemma import is currently wired for Android. iOS can use the shared cloud flow now."
            ),
            cloudChatClient = MultiCloudChatClient()
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    EdgeChatTheme {
        EdgeChatApp(controller = controller)
    }
}

private class IosSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): AppSettings {
        return AppSettings(
            providerMode = defaults.stringForKey(KEY_PROVIDER_MODE)
                ?.let(ProviderMode::valueOf)
                ?: ProviderMode.Cloud,
            localModelPath = defaults.stringForKey(KEY_LOCAL_MODEL_PATH),
            localModelName = defaults.stringForKey(KEY_LOCAL_MODEL_NAME) ?: "",
            localModelSizeBytes = defaults.stringForKey(KEY_LOCAL_MODEL_SIZE)?.toLongOrNull(),
            localBackend = defaults.stringForKey(KEY_LOCAL_BACKEND)
                ?.let(LocalBackend::valueOf)
                ?: LocalBackend.Cpu,
            cloudProvider = defaults.stringForKey(KEY_CLOUD_PROVIDER)
                ?.let(CloudProvider::valueOf)
                ?: CloudProvider.entries.first(),
            allowCloudFallback = defaults.objectForKey(KEY_ALLOW_CLOUD_FALLBACK)?.let {
                defaults.boolForKey(KEY_ALLOW_CLOUD_FALLBACK)
            } ?: true,
            stayOfflineWhenLocal = defaults.objectForKey(KEY_STAY_OFFLINE_WHEN_LOCAL)?.let {
                defaults.boolForKey(KEY_STAY_OFFLINE_WHEN_LOCAL)
            } ?: true
        )
    }

    override fun save(settings: AppSettings) {
        defaults.setObject(settings.providerMode.name, forKey = KEY_PROVIDER_MODE)
        if (settings.localModelPath == null) {
            defaults.removeObjectForKey(KEY_LOCAL_MODEL_PATH)
        } else {
            defaults.setObject(settings.localModelPath, forKey = KEY_LOCAL_MODEL_PATH)
        }
        defaults.setObject(settings.localModelName, forKey = KEY_LOCAL_MODEL_NAME)
        if (settings.localModelSizeBytes == null) {
            defaults.removeObjectForKey(KEY_LOCAL_MODEL_SIZE)
        } else {
            defaults.setObject(settings.localModelSizeBytes.toString(), forKey = KEY_LOCAL_MODEL_SIZE)
        }
        defaults.setObject(settings.localBackend.name, forKey = KEY_LOCAL_BACKEND)
        defaults.setObject(settings.cloudProvider.name, forKey = KEY_CLOUD_PROVIDER)
        defaults.setBool(settings.allowCloudFallback, forKey = KEY_ALLOW_CLOUD_FALLBACK)
        defaults.setBool(settings.stayOfflineWhenLocal, forKey = KEY_STAY_OFFLINE_WHEN_LOCAL)
    }

    companion object {
        private const val KEY_PROVIDER_MODE = "provider_mode"
        private const val KEY_LOCAL_MODEL_PATH = "local_model_path"
        private const val KEY_LOCAL_MODEL_NAME = "local_model_name"
        private const val KEY_LOCAL_MODEL_SIZE = "local_model_size"
        private const val KEY_LOCAL_BACKEND = "local_backend"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_ALLOW_CLOUD_FALLBACK = "allow_cloud_fallback"
        private const val KEY_STAY_OFFLINE_WHEN_LOCAL = "stay_offline_when_local"
    }
}
