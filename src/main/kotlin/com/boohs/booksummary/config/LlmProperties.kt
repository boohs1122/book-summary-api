package com.boohs.booksummary.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("llm")
data class LlmProperties(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: URI = URI("https://generativelanguage.googleapis.com/v1beta"),
    val timeout: Duration = Duration.ofSeconds(60),
)
