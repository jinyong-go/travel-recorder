package com.yong.travel.batch.loginhistory

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 한국 시간 하루 범위로 합계를 내고, 다시 돌리면 덮어쓰며, 받을 수 없는 날짜는 거부하는지 확인한다. */
@SpringBootTest
@SpringBatchTest
class LoginDailyStatsJobTest {

    @Autowired private lateinit var jobOperatorTestUtils: JobOperatorTestUtils
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var loginDailyStatsJob: Job

    private val zone = ZoneId.of("Asia/Seoul")
    private val yesterday = LocalDate.now(zone).minusDays(1)

    @BeforeEach
    fun setUp() {
        // 잡이 둘이라 테스트 도구가 스스로 고르지 못한다. 실행할 잡을 지정한다.
        jobOperatorTestUtils.setJob(loginDailyStatsJob)
        jdbcTemplate.update("DELETE FROM login_daily_stats")
        jdbcTemplate.update("DELETE FROM login_history")
        jdbcTemplate.update("DELETE FROM users")
    }

    @Test
    fun `한국 시간 하루 범위의 로그인 횟수와 사용자 수를 센다`() {
        val alice = newUser("alice")
        val bob = newUser("bob")
        insertHistory(alice, yesterday, LocalTime.MIDNIGHT)
        insertHistory(alice, yesterday, LocalTime.of(23, 59, 59))
        insertHistory(bob, yesterday, LocalTime.NOON)
        // 범위 밖: 전날의 마지막 순간과 다음 날 0시
        insertHistory(bob, yesterday.minusDays(1), LocalTime.of(23, 59, 59))
        insertHistory(bob, yesterday.plusDays(1), LocalTime.MIDNIGHT)

        assertEquals(BatchStatus.COMPLETED, run(yesterday))
        assertEquals(3L to 2L, statOf(yesterday))
    }

    @Test
    fun `대상 날짜를 생략하면 어제를 집계하고 로그인이 없던 날도 0 으로 남긴다`() {
        val execution = jobOperatorTestUtils.startJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(0L to 0L, statOf(yesterday))
    }

    @Test
    fun `같은 날짜를 다시 집계하면 덮어쓴다`() {
        val alice = newUser("alice")
        insertHistory(alice, yesterday, LocalTime.NOON)
        run(yesterday)
        insertHistory(newUser("bob"), yesterday, LocalTime.NOON)

        run(yesterday)

        assertEquals(2L to 2L, statOf(yesterday))
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM login_daily_stats", Int::class.java))
    }

    @Test
    fun `끝나지 않은 날과 원본 보관 기간이 지났을 수 있는 날은 거부한다`() {
        val today = LocalDate.now(zone)

        assertEquals(BatchStatus.FAILED, run(today))
        assertEquals(BatchStatus.FAILED, run(today.minusDays(91)))
        assertTrue(jdbcTemplate.queryForList("SELECT stat_date FROM login_daily_stats").isEmpty())
    }

    /**
     * 같은 날짜를 거듭 돌려도 새 실행으로 잡히도록 고유 파라미터에 대상 날짜를 얹는다.
     *
     * 실행 결과(`JobExecution`)가 아니라 상태를 돌려준다. `@SpringBatchTest` 는 테스트 클래스에서
     * `JobExecution` 을 돌려주는 메서드를 스코프용 팩토리로 보고 매 테스트 전에 호출하려 든다.
     */
    private fun run(date: LocalDate): BatchStatus =
        jobOperatorTestUtils.startJob(
            jobOperatorTestUtils.uniqueJobParametersBuilder
                .addString("targetDate", date.toString())
                .toJobParameters(),
        ).status

    private fun statOf(date: LocalDate): Pair<Long, Long> =
        jdbcTemplate.queryForObject(
            "SELECT login_count, unique_user_count FROM login_daily_stats WHERE stat_date = ?",
            { rs, _ -> rs.getLong(1) to rs.getLong(2) },
            date,
        )!!

    private fun newUser(providerId: String): Long {
        jdbcTemplate.update(
            "INSERT INTO users (provider, provider_id, name, created_at) VALUES ('local', ?, '테스터', ?)",
            providerId,
            Timestamp.from(Instant.now()),
        )
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE provider_id = ?", Long::class.java, providerId)!!
    }

    private fun insertHistory(userId: Long, date: LocalDate, time: LocalTime) {
        jdbcTemplate.update(
            "INSERT INTO login_history (user_id, ip_address, logged_in_at) VALUES (?, '203.0.113.7', ?)",
            userId,
            Timestamp.from(date.atTime(time).atZone(zone).toInstant()),
        )
    }
}
