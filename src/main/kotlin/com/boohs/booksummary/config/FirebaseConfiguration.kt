package com.boohs.booksummary.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(FirebaseProperties::class)
class FirebaseConfiguration
