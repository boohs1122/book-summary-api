package com.boohs.booksummary.config

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.springframework.stereotype.Component

@Component
class FirebaseTokenVerifier(
    private val properties: FirebaseProperties,
) {
    private val firebaseAuth: FirebaseAuth by lazy {
        check(properties.projectId.isNotBlank()) { "FIREBASE_PROJECT_ID is required" }
        val app =
            FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(
                    FirebaseOptions
                        .builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(properties.projectId)
                        .build(),
                    APP_NAME,
                )
        FirebaseAuth.getInstance(app)
    }

    fun verify(idToken: String): String =
        try {
            firebaseAuth.verifyIdToken(idToken).uid
        } catch (_: FirebaseAuthException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

    companion object {
        private const val APP_NAME = "booksummary-auth"
    }
}
