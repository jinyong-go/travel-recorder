package com.yong.travel

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 필터체인의 인증 관문 검증.
 *
 * 비로그인에게 열린 경로만 나열하고 나머지는 닫는 구성이라, 열린 경로가 실제로 열려 있는지와
 * 그 밖의 경로가 컨트롤러에 닿기 전에 같은 오류 본문으로 막히는지를 함께 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `비로그인에게 열린 경로는 로그인 없이 통과한다`() {
        listOf(
            "/api/trips?scope=PUBLIC",
            "/api/records?scope=PUBLIC",
            "/api/tags",
            "/api/auth/session",
        ).forEach { url ->
            mockMvc.perform(get(url)).andExpect(status().isOk)
        }
    }

    @Test
    fun `그 밖의 경로는 필터에서 401 UNAUTHENTICATED 오류 본문으로 막힌다`() {
        listOf(
            get("/api/auth/me"),
            get("/api/places/search").param("keyword", "카페"),
            get("/api/invites"),
            get("/api/there-is-no-such-path"),
            post("/api/auth/logout").with(csrf()),
        ).forEach { request ->
            mockMvc.perform(request)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.status").value(401))
        }
    }

    @Test
    fun `CORS 사전 요청은 쿠키 없이도 허용 출처에 통과한다`() {
        mockMvc.perform(
            options("/api/trips")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
    }
}
