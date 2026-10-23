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
 * 로그인 이력에서 하루 단위 합계(로그인 횟수·로그인한 사용자 수)를 만든다.
 *
 * 하루는 한국 시간 0시부터 다음 날 0시 전까지다. 같은 날짜를 다시 돌리면 덮어쓰며,
 * 로그인이 없던 날도 0 으로 남겨 "집계하지 않음"과 구분한다.
 */
@Configuration
class LoginDailyStatsJobConfig(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${app.login-history.retention-days}") private val retentionDays: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인 통계 잡. 스텝 하나로 끝난다. */
    @Bean
    fun loginDailyStatsJob(jobRepository: JobRepository, loginDailyStatsStep: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(loginDailyStatsStep)
            .build()

    /** 집계 태스크릿을 트랜잭션 하나로 도는 스텝. */
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
     * 하루치 합계를 넣거나 덮어쓰는 태스크릿. 잡 파라미터 `targetDate` 를 읽어야 하므로 스텝이 실행될 때 만든다.
     *
     * @param targetDate 집계할 날짜(`yyyy-MM-dd`). 없으면 한국 시간 기준 어제다
     * @return 태스크릿. 거부한 날짜면 실행 중에 `IllegalArgumentException` 을 던진다
     */
    @Bean
    @StepScope
    fun loginDailyStatsTasklet(@Value("#{jobParameters['targetDate']}") targetDate: String?): Tasklet =
        Tasklet { contribution, _ ->
            val date = resolveTargetDate(targetDate)
            val from = date.atStartOfDay(ZONE).toInstant()
            val to = date.plusDays(1).atStartOfDay(ZONE).toInstant()

            // 같은 날짜를 다시 돌리면 덮어쓴다. 집계 함수만 쓰는 SELECT 는 대상 행이 없어도 한 행(0, 0)을
            // 돌려준다. 로그인이 없던 날도 남는 이유다.
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
     *
     * 오늘 이후는 아직 끝나지 않은 날이라 거부한다. 원본 보관 기간이 지났을 수 있는 날짜도 거부한다 —
     * 원본이 지워진 만큼 실제 값이 작게 덮이고, 그 손실은 되돌릴 수 없다.
     * 거부하면 `IllegalArgumentException` 을 던지므로 잡이 실패로 끝나 종료 코드로 드러난다.
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
