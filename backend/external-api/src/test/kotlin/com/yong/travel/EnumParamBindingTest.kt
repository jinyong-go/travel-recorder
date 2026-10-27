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
 * 열거형 값은 쿼리·본문·응답 모두 대문자 한 가지 표기다. 소문자를 다시 받아 주는 변환기가 끼어들면
 * 같은 값이 두 이름을 갖게 되므로, 소문자가 거부되는 것까지 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnumParamBindingTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `대문자 표기의 scope 와 sort 가 바인딩된다`() {
        listOf(
            "/api/trips?scope=PUBLIC",
            "/api/trips?scope=PUBLIC&sort=RECENT",
            "/api/trips?scope=PUBLIC&sort=START_DATE",
            "/api/records?scope=PUBLIC",
            "/api/records?scope=PUBLIC&sort=RECENT",
            "/api/records?scope=PUBLIC&sort=OLDEST",
            "/api/records?scope=PUBLIC&category=FOOD",
        ).forEach { url ->
            mockMvc.perform(get(url)).andExpect(status().isOk)
        }
    }

    @Test
    fun `소문자와 알 수 없는 값은 400 VALIDATION_ERROR 다`() {
        listOf(
            "/api/trips?scope=public",
            "/api/trips?scope=PUBLIC&sort=startDate",
            "/api/records?scope=PUBLIC&sort=oldest",
            "/api/trips?scope=EVERYTHING",
            "/api/trips?scope=PUBLIC&sort=DISTANCE",
        ).forEach { url ->
            mockMvc.perform(get(url))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        }
    }

    @Test
    fun `기록 목록은 scope 와 tripId 가 둘 다 없으면 400 VALIDATION_ERROR 다`() {
        mockMvc.perform(get("/api/records"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `비로그인은 MINE 과 SHARED 를 조회할 수 없다`() {
        listOf("MINE", "SHARED").forEach { scope ->
            mockMvc.perform(get("/api/trips?scope=$scope"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }
}
