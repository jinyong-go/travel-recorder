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
 * 보관 기간이 지난 로그인 이력을 지우는 잡.
 *
 * 접속 IP·User-Agent 는 개인정보라 기한 없이 두지 않는다. 삭제 기준은 "실행 시각 - 보관 기간"
 * 이며 경계 시각의 행은 남긴다. 같은 날 두 번 돌려도 결과가 같아 재시작 정보가 필요 없다 —
 * 그래서 배치 메타데이터 테이블 없이 도는 저장소를 쓴다.
 */
@Configuration
class LoginHistoryCleanupJobConfig(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${app.login-history.retention-days}") private val retentionDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun loginHistoryCleanupJob(jobRepository: JobRepository, loginHistoryCleanupStep: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(loginHistoryCleanupStep)
            .build()

    @Bean
    fun loginHistoryCleanupStep(jobRepository: JobRepository, transactionManager: PlatformTransactionManager): Step =
        StepBuilder("loginHistoryCleanupStep", jobRepository)
            .tasklet({ contribution, _ ->
                val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
                // 한 문장으로 지운다. 하루치씩 쌓이는 양이라 나눠 지울 이유가 아직 없다.
                val deleted = jdbcTemplate.update(
                    "DELETE FROM login_history WHERE logged_in_at < ?",
                    Timestamp.from(cutoff),
                )
                contribution.incrementWriteCount(deleted.toLong())
                // 지운 건수와 기준 시각만 남긴다. 지운 행의 IP·User-Agent 를 로그가 대신 보관하지 않는다.
                log.info("로그인 이력 정리 cutoff={} 삭제={}건", cutoff, deleted)
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    companion object {
        const val JOB_NAME = "loginHistoryCleanupJob"
    }
}
