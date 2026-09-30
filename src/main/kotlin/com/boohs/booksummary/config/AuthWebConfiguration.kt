package com.boohs.booksummary.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AuthWebConfiguration(
    private val tokenVerifier: FirebaseTokenVerifier,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(FirebaseAuthInterceptor(tokenVerifier::verify)).addPathPatterns("/api/v1/**")
    }
}
