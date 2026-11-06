package io.github.chatgpt01.petai.chat

import java.net.URI

/** Non-secret settings only. baseUrl is the API root, e.g. https://host/v1. */
data class ChatModelConfig(
    val baseUrl: String,
    val model: String,
    val temperature: Double = 1.0,
    val maxOutputTokens: Int = 1024,
    val timeoutSeconds: Int = 60,
) {
    init {
        // Do not echo a rejected URL: a user may have pasted credentials into it.
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) { "An HTTPS API root without credentials is required" }
        require(model.isNotBlank()) { "Model name is required" }
        require(temperature.isFinite() && temperature in 0.0..2.0) { "Invalid temperature" }
        require(maxOutputTokens > 0) { "Output token limit must be positive" }
        require(timeoutSeconds in 1..300) { "Timeout must be between 1 and 300 seconds" }
    }
}

/** Deliberately not a data class: diagnostics and generated copy/component methods must not expose it. */
class ApiKey(private val value: String) {
    init {
        require(value.isNotBlank() && value.none { it.isISOControl() }) { "Invalid API key" }
    }

    /** Only the encrypted credential store and HTTPS transport should call this. Never log it. */
    fun reveal(): String = value

    override fun toString(): String = "ApiKey([REDACTED])"
}
