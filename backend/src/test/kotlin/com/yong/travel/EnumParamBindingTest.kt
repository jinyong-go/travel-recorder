package com.yong.travel

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 쿼리 파라미터의 열거형 표기 검증.
 *
 * 명세(§4.1, §4.3.1, §4.4.1)는 소문자·camelCase 로 규정하는데 Spring 기본 변환기는 대문자만
 * 받는다. 프론트엔드가 명세대로 보내면 전부 400 이 되던 자리라 회귀 테스트로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnumParamBindingTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `명세 표기 그대로 보낸 scope 와 sort 가 바인딩된다`() {
        listOf(
            "/api/trips?scope=public",
            "/api/trips?scope=public&sort=recent",
            "/api/trips?scope=public&sort=startDate",
            "/api/records?scope=public",
            "/api/records?scope=public&sort=recent",
            "/api/records?scope=public&category=FOOD",
        ).forEach { url ->
            mockMvc.perform(get(url)).andExpect(status().isOk)
        }
    }

    @Test
    fun `대문자 표기도 그대로 받는다`() {
        mockMvc.perform(get("/api/trips?scope=PUBLIC&sort=START_DATE")).andExpect(status().isOk)
    }

    @Test
    fun `알 수 없는 값은 400 VALIDATION_ERROR 다`() {
        mockMvc.perform(get("/api/trips?scope=everything"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))

        mockMvc.perform(get("/api/trips?scope=public&sort=distance"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `비로그인은 mine 과 shared 를 조회할 수 없다`() {
        listOf("mine", "shared").forEach { scope ->
            mockMvc.perform(get("/api/trips?scope=$scope"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }
}
