package com.boohs.booksummary.controller

import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.common.ErrorResponse
import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.domain.JobType
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.dto.JobResponse
import com.boohs.booksummary.service.SummaryJobService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/jobs")
class JobController(
    private val jobs: SummaryJobService,
) {
    @GetMapping("/{jobId}")
    fun get(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable jobId: String,
    ): JobResponse {
        val job = jobs.get(uid, jobId)
        return JobResponse(
            jobId = job.id,
            type = job.type.name,
            status = job.status.name,
            createdAt = job.createdAt,
            documentId = if (job.status == ProcessingStatus.PROCESSING) null else job.document.id,
            completedAt = job.completedAt,
            error =
                job.errorCode?.let { code ->
                    val message =
                        when {
                            job.type == JobType.QUIZ && code == ErrorCode.LLM_FAILED -> {
                                "퀴즈 생성에 실패했습니다. 잠시 후 다시 시도해 주세요."
                            }

                            job.type == JobType.QUIZ && code == ErrorCode.LLM_INVALID_RESPONSE -> {
                                "퀴즈 결과가 올바르지 않습니다. 다시 시도해 주세요."
                            }

                            else -> {
                                code.defaultMessage
                            }
                        }
                    ErrorResponse.ErrorBody(code.name, message)
                },
            quizId = job.quizId,
        )
    }
}
