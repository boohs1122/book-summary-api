package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.SummaryContent
import com.boohs.booksummary.llm.LlmClient
import com.boohs.booksummary.llm.validator.SummaryValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SummaryWorker(
    private val jobs: SummaryJobService,
    private val llmClient: LlmClient,
    private val validator: SummaryValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(jobId: String) {
        try {
            val work = jobs.start(jobId) ?: return
            jobs.complete(jobId, validatedSummary(work.text))
        } catch (e: BusinessException) {
            jobs.fail(jobId, e.errorCode)
        } catch (e: Exception) {
            log.error("요약 작업 실패 jobId={} exceptionType={}", jobId, e.javaClass.simpleName)
            jobs.fail(jobId, ErrorCode.LLM_FAILED)
        }
    }

    private fun validatedSummary(text: String): SummaryContent {
        val keyPointCount = SummarySizing.keyPointCount(text.length)
        repeat(2) { attempt ->
            try {
                return validator.parse(llmClient.generateSummary(text, keyPointCount), keyPointCount)
            } catch (e: BusinessException) {
                if (e.errorCode != ErrorCode.LLM_INVALID_RESPONSE || attempt == 1) throw e
            }
        }
        throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
    }
}
