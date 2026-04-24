package com.example.edgedevicedemo.data

import android.content.Context
import com.example.edgedevicedemo.shared.model.AppSettings
import com.example.edgedevicedemo.shared.model.CloudProvider
import com.example.edgedevicedemo.shared.model.LocalBackend
import com.example.edgedevicedemo.shared.model.ProviderMode
import com.example.edgedevicedemo.shared.platform.SettingsStore

class AppPreferences(context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        return AppSettings(
            providerMode = preferences.getString(KEY_PROVIDER_MODE, ProviderMode.Local.name)
                ?.let(ProviderMode::valueOf)
                ?: ProviderMode.Local,
            localModelPath = preferences.getString(KEY_LOCAL_MODEL_PATH, null),
            localModelName = preferences.getString(KEY_LOCAL_MODEL_NAME, "") ?: "",
            localModelSizeBytes = preferences.takeIf { it.contains(KEY_LOCAL_MODEL_SIZE) }
                ?.getLong(KEY_LOCAL_MODEL_SIZE, 0L),
            localBackend = preferences.getString(KEY_LOCAL_BACKEND, LocalBackend.Gpu.name)
                ?.let(LocalBackend::valueOf)
                ?: LocalBackend.Gpu,
            cloudProvider = preferences.getString(KEY_CLOUD_PROVIDER, CloudProvider.entries.first().name)
                ?.let(CloudProvider::valueOf)
                ?: CloudProvider.entries.first(),
            allowCloudFallback = preferences.getBoolean(KEY_ALLOW_CLOUD_FALLBACK, true),
            stayOfflineWhenLocal = preferences.getBoolean(KEY_STAY_OFFLINE_WHEN_LOCAL, true)
        )
    }

    override fun save(settings: AppSettings) {
        val localModelSizeBytes = settings.localModelSizeBytes
        preferences.edit()
            .putString(KEY_PROVIDER_MODE, settings.providerMode.name)
            .putString(KEY_LOCAL_MODEL_PATH, settings.localModelPath)
            .putString(KEY_LOCAL_MODEL_NAME, settings.localModelName)
            .apply {
                if (localModelSizeBytes == null) {
                    remove(KEY_LOCAL_MODEL_SIZE)
                } else {
                    putLong(KEY_LOCAL_MODEL_SIZE, localModelSizeBytes)
                }
            }
            .putString(KEY_LOCAL_BACKEND, settings.localBackend.name)
            .putString(KEY_CLOUD_PROVIDER, settings.cloudProvider.name)
            .putBoolean(KEY_ALLOW_CLOUD_FALLBACK, settings.allowCloudFallback)
            .putBoolean(KEY_STAY_OFFLINE_WHEN_LOCAL, settings.stayOfflineWhenLocal)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "edge_chat_prefs"
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
