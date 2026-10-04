package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.llm.LlmClient
import com.boohs.booksummary.llm.validator.QuizValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QuizWorker(
    private val jobs: SummaryJobService,
    private val llm: LlmClient,
    private val validator: QuizValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(jobId: String) {
        try {
            val work = jobs.startQuiz(jobId) ?: return
            jobs.completeQuiz(jobId, "quz_${Ulid.generate()}", validated(work))
        } catch (e: BusinessException) {
            jobs.failQuiz(jobId, e.errorCode)
        } catch (e: Exception) {
            log.error("퀴즈 작업 실패 jobId={} exceptionType={}", jobId, e.javaClass.simpleName)
            jobs.failQuiz(jobId, ErrorCode.LLM_FAILED)
        }
    }

    private fun validated(work: QuizWork): String {
        repeat(2) { attempt ->
            try {
                return validator.serialize(validator.parse(llm.generateQuiz(work.text, work.summaryJson, QuizValidator.QUESTION_COUNT)))
            } catch (e: BusinessException) {
                if (e.errorCode != ErrorCode.LLM_INVALID_RESPONSE || attempt == 1) throw e
            }
        }
        throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
    }
}
