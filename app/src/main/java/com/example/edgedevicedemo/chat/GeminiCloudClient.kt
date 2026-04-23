package com.example.edgedevicedemo.chat

import com.example.edgedevicedemo.ui.ChatRole
import com.example.edgedevicedemo.ui.CloudTurn
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiCloudClient {
    suspend fun generateReply(
        apiKey: String,
        modelName: String,
        turns: List<CloudTurn>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add a Gemini API key in Settings to use cloud chat.")
        }

        val normalizedModel = normalizeModelName(modelName)
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent?key=" +
                URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        )
        val payload = buildPayload(turns)

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val responseText = (
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            )?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException(parseErrorMessage(responseText))
            }

            parseSuccessMessage(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(turns: List<CloudTurn>): JSONObject {
        val contents = JSONArray()
        turns.forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.User) "user" else "model")
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", turn.text)
                        )
                    )
            )
        }

        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "You are a helpful mobile assistant. Keep replies concise and practical."
                        )
                    )
                )
            )
            .put("contents", contents)
    }

    private fun parseSuccessMessage(responseText: String): String {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException(parseErrorMessage(responseText))

        if (candidates.length() == 0) {
            throw IOException("Gemini returned no response.")
        }

        val parts = candidates
            .getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IOException("Gemini returned an empty response.")

        val builder = StringBuilder()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            builder.append(part.optString("text"))
        }

        return builder.toString().trim().ifEmpty {
            throw IOException("Gemini returned an empty text response.")
        }
    }

    private fun parseErrorMessage(responseText: String): String {
        return runCatching {
            val root = JSONObject(responseText)
            root.optJSONObject("error")?.optString("message")
                ?: root.optJSONObject("promptFeedback")?.toString()
                ?: "Gemini request failed."
        }.getOrDefault("Gemini request failed.")
    }

    private fun normalizeModelName(modelName: String): String {
        val trimmed = modelName.trim().ifEmpty { DEFAULT_MODEL }
        return trimmed.removePrefix("models/")
    }

    companion object {
        private const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
    }
}
