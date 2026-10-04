package com.boohs.booksummary.controller

import com.boohs.booksummary.common.ErrorResponse
import com.boohs.booksummary.config.FirebaseAuthInterceptor
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
            error = job.errorCode?.let { ErrorResponse.ErrorBody(it.name, it.defaultMessage) },
        )
    }
}
