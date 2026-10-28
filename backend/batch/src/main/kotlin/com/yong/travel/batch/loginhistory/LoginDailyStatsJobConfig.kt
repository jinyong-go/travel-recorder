package com.yong.travel.batch.loginhistory

import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 로그인 이력으로 하루 단위 로그인 횟수와 사용자 수를 집계한다.
 * 로그인이 없던 날도 0 으로 남겨 집계하지 않은 날과 구분한다.
 */
@Configuration
class LoginDailyStatsJobConfig(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${app.login-history.retention-days}") private val retentionDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인 통계 잡. */
    @Bean
    fun loginDailyStatsJob(jobRepository: JobRepository, loginDailyStatsStep: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(loginDailyStatsStep)
            .build()

    /** 집계 스텝. */
    @Bean
    fun loginDailyStatsStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        loginDailyStatsTasklet: Tasklet,
    ): Step =
        StepBuilder("loginDailyStatsStep", jobRepository)
            .tasklet(loginDailyStatsTasklet, transactionManager)
            .build()

    /**
     * 하루치 합계를 넣거나 덮어쓴다.
     *
     * @param targetDate 집계할 날짜(`yyyy-MM-dd`). 없으면 한국 시간 기준 어제다
     */
    @Bean
    @StepScope
    fun loginDailyStatsTasklet(@Value("#{jobParameters['targetDate']}") targetDate: String?): Tasklet =
        Tasklet { contribution, _ ->
            val date = resolveTargetDate(targetDate)
            val from = date.atStartOfDay(ZONE).toInstant()
            val to = date.plusDays(1).atStartOfDay(ZONE).toInstant()

            // 같은 날짜를 다시 돌리면 덮어쓴다. 로그인이 없어도 (0, 0) 한 행이 남는다.
            jdbcTemplate.update(
                """
                INSERT INTO login_daily_stats (stat_date, login_count, unique_user_count, aggregated_at)
                SELECT ?, COUNT(*), COUNT(DISTINCT user_id), ?
                FROM login_history
                WHERE logged_in_at >= ? AND logged_in_at < ?
                ON CONFLICT (stat_date) DO UPDATE
                    SET login_count = EXCLUDED.login_count,
                        unique_user_count = EXCLUDED.unique_user_count,
                        aggregated_at = EXCLUDED.aggregated_at
                """.trimIndent(),
                date,
                Timestamp.from(Instant.now()),
                Timestamp.from(from),
                Timestamp.from(to),
            )
            contribution.incrementWriteCount(1)
            log.info("로그인 통계 집계 date={}", date)
            RepeatStatus.FINISHED
        }

    /**
     * 집계할 날짜를 정한다. 없으면 한국 시간 기준 어제다.
     * 끝나지 않은 날과, 원본이 지워져 값이 작게 덮일 수 있는 오래된 날은 `IllegalArgumentException` 으로 거부한다.
     */
    private fun resolveTargetDate(targetDate: String?): LocalDate {
        val today = LocalDate.now(ZONE)
        val date = targetDate?.let(LocalDate::parse) ?: today.minusDays(1)
        require(date.isBefore(today)) { "아직 끝나지 않은 날짜는 집계하지 않는다: $date" }
        require(!date.isBefore(today.minusDays(retentionDays))) {
            "원본 이력의 보관 기간이 지났을 수 있는 날짜다: $date"
        }
        return date
    }

    companion object {
        const val JOB_NAME = "loginDailyStatsJob"
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
