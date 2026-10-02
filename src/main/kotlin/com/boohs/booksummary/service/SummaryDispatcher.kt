package com.boohs.booksummary.service

import com.boohs.booksummary.common.ErrorCode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SummaryDispatcher(
    @Qualifier("summaryExecutor") private val executor: TaskExecutor,
    private val worker: SummaryWorker,
    private val jobs: SummaryJobService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRequested(event: SummaryRequested) {
        try {
            executor.execute { worker.generate(event.jobId) }
        } catch (_: TaskRejectedException) {
            jobs.fail(event.jobId, ErrorCode.RATE_LIMITED)
        }
    }
}
