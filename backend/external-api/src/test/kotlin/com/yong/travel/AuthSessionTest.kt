package com.yong.travel

import com.jayway.jsonpath.JsonPath
import com.yong.travel.auth.security.LoginUserDetails
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** CSRF 토큰 발급·사용과 로그아웃 시 토큰 정리를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `비로그인도 세션 조회는 200 이고 HttpOnly 토큰 쿠키와 본문 토큰을 준다`() {
        val result = mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.csrfToken").isNotEmpty)
            .andReturn()

        val cookie = result.response.getCookie("XSRF-TOKEN")
        assertThat(cookie).isNotNull
        assertThat(cookie!!.isHttpOnly).isTrue()
    }

    @Test
    fun `세션 조회로 받은 토큰을 헤더에 실으면 쓰기 요청이 통과한다`() {
        val (cookie, token) = issueToken()

        mockMvc.perform(post("/api/auth/logout").with(loggedIn()).cookie(cookie).header("X-XSRF-TOKEN", token))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `토큰 헤더가 없으면 쓰기 요청은 403 이다`() {
        val (cookie, _) = issueToken()

        mockMvc.perform(post("/api/auth/logout").with(loggedIn()).cookie(cookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `로그아웃은 토큰 쿠키를 비운다`() {
        val (cookie, token) = issueToken()

        val result = mockMvc.perform(post("/api/auth/logout").with(loggedIn()).cookie(cookie).header("X-XSRF-TOKEN", token))
            .andReturn()

        val cleared = result.response.getCookie("XSRF-TOKEN")
        assertThat(cleared).isNotNull
        assertThat(cleared!!.maxAge).isZero()
    }

    /** 세션 조회로 토큰 쿠키와, 헤더에 실을 본문 토큰을 받는다. */
    private fun issueToken(): Pair<Cookie, String> {
        val response = mockMvc.perform(get("/api/auth/session")).andReturn().response
        val token = JsonPath.read<String>(response.contentAsString, "$.csrfToken")
        return response.getCookie("XSRF-TOKEN")!! to token
    }

    /** 로그아웃은 로그인이 필요해 로그인 상태로 보낸다. */
    private fun loggedIn() = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", 1L), null, emptyList()),
    )
}
