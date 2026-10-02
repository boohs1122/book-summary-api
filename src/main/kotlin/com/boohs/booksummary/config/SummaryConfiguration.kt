package com.boohs.booksummary.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@EnableScheduling
@EnableConfigurationProperties(LlmProperties::class)
class SummaryConfiguration {
    @Bean
    fun llmHttpClient(): HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Bean
    fun summaryExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            queueCapacity = 100
            setThreadNamePrefix("summary-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(150)
        }
}
