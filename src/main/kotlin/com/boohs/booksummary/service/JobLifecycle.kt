package com.boohs.booksummary.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JobLifecycle(
    private val jobs: SummaryJobService,
) {
    private val processStartedAt = Instant.now()
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recover() {
        val count = jobs.recoverInterruptedJobs(processStartedAt)
        if (count > 0) log.info("중단된 생성 작업 {}개를 실패 상태로 복구", count)
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    fun cleanup() {
        jobs.deleteExpiredJobs(Instant.now())
    }
}
