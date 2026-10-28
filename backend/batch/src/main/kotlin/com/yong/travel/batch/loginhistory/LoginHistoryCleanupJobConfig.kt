package com.yong.travel.batch.loginhistory

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 보관 기간이 지난 로그인 이력을 지운다.
 * IP·User-Agent 는 개인정보라 기한 없이 두지 않는다.
 */
@Configuration
class LoginHistoryCleanupJobConfig(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${app.login-history.retention-days}") private val retentionDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인 이력 정리 잡. */
    @Bean
    fun loginHistoryCleanupJob(jobRepository: JobRepository, loginHistoryCleanupStep: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(loginHistoryCleanupStep)
            .build()

    /** 보관 기간이 지난 이력을 지우는 스텝. */
    @Bean
    fun loginHistoryCleanupStep(jobRepository: JobRepository, transactionManager: PlatformTransactionManager): Step =
        StepBuilder("loginHistoryCleanupStep", jobRepository)
            .tasklet({ contribution, _ ->
                val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
                val deleted = jdbcTemplate.update(
                    "DELETE FROM login_history WHERE logged_in_at < ?",
                    Timestamp.from(cutoff),
                )
                contribution.incrementWriteCount(deleted.toLong())
                log.info("로그인 이력 정리 cutoff={} deleted={}", cutoff, deleted)
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    companion object {
        const val JOB_NAME = "loginHistoryCleanupJob"
    }
}
