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
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

/** 보관 기간을 넘긴 행만 지우고, 그 안의 행은 남기는지 확인한다. */
@SpringBootTest
@SpringBatchTest
class LoginHistoryCleanupJobTest {

    @Autowired private lateinit var jobOperatorTestUtils: JobOperatorTestUtils
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var loginHistoryCleanupJob: Job

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        // 잡이 둘이라 테스트 도구가 스스로 고르지 못한다. 실행할 잡을 지정한다.
        jobOperatorTestUtils.setJob(loginHistoryCleanupJob)
        jdbcTemplate.update("DELETE FROM login_history")
        jdbcTemplate.update("DELETE FROM users")
        jdbcTemplate.update(
            "INSERT INTO users (provider, provider_id, name, created_at) VALUES ('local', 'tester', '테스터', ?)",
            Timestamp.from(Instant.now()),
        )
        userId = jdbcTemplate.queryForObject("SELECT id FROM users", Long::class.java)!!
    }

    @Test
    fun `보관 기간을 넘긴 이력만 지운다`() {
        insertHistory(daysAgo = 91, userAgent = "old")
        insertHistory(daysAgo = 89, userAgent = "recent")

        val execution = jobOperatorTestUtils.startJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(1, execution.stepExecutions.single().writeCount)
        assertEquals(
            listOf("recent"),
            jdbcTemplate.queryForList("SELECT user_agent FROM login_history", String::class.java),
        )
    }

    @Test
    fun `지울 이력이 없어도 정상 종료한다`() {
        insertHistory(daysAgo = 1, userAgent = "recent")

        val execution = jobOperatorTestUtils.startJob()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(0, execution.stepExecutions.single().writeCount)
    }

    private fun insertHistory(daysAgo: Long, userAgent: String) {
        jdbcTemplate.update(
            "INSERT INTO login_history (user_id, ip_address, user_agent, logged_in_at) VALUES (?, '203.0.113.7', ?, ?)",
            userId,
            userAgent,
            Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)),
        )
    }
}
