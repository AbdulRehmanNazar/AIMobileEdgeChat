package com.example.edgedevicedemo.data

import android.content.Context
import com.example.edgedevicedemo.ui.AppSettings
import com.example.edgedevicedemo.ui.LocalBackend
import com.example.edgedevicedemo.ui.ProviderMode

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
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
            cloudModelName = preferences.getString(KEY_CLOUD_MODEL_NAME, DEFAULT_CLOUD_MODEL)
                ?: DEFAULT_CLOUD_MODEL,
            cloudApiKey = preferences.getString(KEY_CLOUD_API_KEY, "") ?: "",
            allowCloudFallback = preferences.getBoolean(KEY_ALLOW_CLOUD_FALLBACK, true),
            stayOfflineWhenLocal = preferences.getBoolean(KEY_STAY_OFFLINE_WHEN_LOCAL, true)
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_PROVIDER_MODE, settings.providerMode.name)
            .putString(KEY_LOCAL_MODEL_PATH, settings.localModelPath)
            .putString(KEY_LOCAL_MODEL_NAME, settings.localModelName)
            .apply {
                if (settings.localModelSizeBytes == null) {
                    remove(KEY_LOCAL_MODEL_SIZE)
                } else {
                    putLong(KEY_LOCAL_MODEL_SIZE, settings.localModelSizeBytes)
                }
            }
            .putString(KEY_LOCAL_BACKEND, settings.localBackend.name)
            .putString(KEY_CLOUD_MODEL_NAME, settings.cloudModelName)
            .putString(KEY_CLOUD_API_KEY, settings.cloudApiKey)
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
        private const val KEY_CLOUD_MODEL_NAME = "cloud_model_name"
        private const val KEY_CLOUD_API_KEY = "cloud_api_key"
        private const val KEY_ALLOW_CLOUD_FALLBACK = "allow_cloud_fallback"
        private const val KEY_STAY_OFFLINE_WHEN_LOCAL = "stay_offline_when_local"

        private const val DEFAULT_CLOUD_MODEL = "gemini-2.5-flash-lite"
    }
}
