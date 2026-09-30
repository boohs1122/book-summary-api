package com.boohs.booksummary.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "firebase")
data class FirebaseProperties(
    val projectId: String = "",
)
