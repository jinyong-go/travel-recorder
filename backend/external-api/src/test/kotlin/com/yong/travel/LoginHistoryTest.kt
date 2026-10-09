package com.yong.travel

import com.yong.travel.auth.persistence.LoginHistoryEntity
import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.LoginUserDetails
import com.yong.travel.auth.service.LoginHistoryService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 로그인 이력이 남는 모양과, 조회가 본인 것으로만 한정되는지 검증.
 *
 * 기록 지점인 임시 로그인 API 는 `local`·`dev` 프로파일에만 있어 여기서는 서비스로 기록한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginHistoryTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var loginHistoryService: LoginHistoryService

    @Test
    fun `기록한 IP 와 User-Agent 가 최신순으로 조회된다`() {
        val user = newUser()
        loginHistoryService.record(user, "203.0.113.7", "first-agent")
        loginHistoryService.record(user, "2001:db8::1", "second-agent")
        flush()

        val history = loginHistoryService.list(user, page()).content

        assertEquals(listOf("second-agent", "first-agent"), history.map { it.userAgent })
        assertEquals("2001:db8::1", history.first().ipAddress)
    }

    @Test
    fun `긴 User-Agent 는 잘라 저장하고 없으면 null 로 둔다`() {
        val user = newUser()
        loginHistoryService.record(user, "203.0.113.7", "a".repeat(LoginHistoryEntity.USER_AGENT_MAX_LENGTH + 100))
        flush()
        loginHistoryService.record(user, "203.0.113.7", null)
        flush()

        val history = loginHistoryService.list(user, page()).content

        assertNull(history.first().userAgent)
        assertEquals(LoginHistoryEntity.USER_AGENT_MAX_LENGTH, history.last().userAgent?.length)
    }

    @Test
    fun `조회 API 는 요청자 본인의 이력만 준다`() {
        val me = newUser()
        val other = newUser()
        loginHistoryService.record(me, "203.0.113.7", "my-agent")
        loginHistoryService.record(other, "198.51.100.9", "other-agent")
        flush()

        mockMvc.perform(get("/api/auth/me/login-history").with(loggedInAs(me)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].userAgent").value("my-agent"))
            .andExpect(jsonPath("$.content[0].ipAddress").value("203.0.113.7"))
    }

    @Test
    fun `비로그인은 401 이다`() {
        mockMvc.perform(get("/api/auth/me/login-history"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    /** 컨트롤러가 principal 을 `LoginUser` 로 받으므로 임시 로그인과 같은 타입을 세션 대신 직접 넣는다. */
    private fun loggedInAs(userId: Long) = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", userId), null, emptyList()),
    )

    private fun page() = 0

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun newUser(): Long = requireNotNull(
        userRepository.save(
            UserEntity(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = "tester-${System.nanoTime()}@example.com",
                name = "테스터",
            ),
        ).id,
    )
}
