package com.yong.travel

import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 세션 조회로 받은 CSRF 토큰이 쓰기 요청에 그대로 통하는지, 토큰 쿠키가 JS 에 닫혀 있는지,
 * 로그아웃이 토큰을 비우는지 확인한다 (명세 §7).
 */
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

        mockMvc.perform(post("/api/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", token))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `토큰 헤더가 없으면 쓰기 요청은 403 이다`() {
        val (cookie, _) = issueToken()

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `로그아웃은 토큰 쿠키를 비운다`() {
        val (cookie, token) = issueToken()

        val result = mockMvc.perform(post("/api/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", token))
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
}
