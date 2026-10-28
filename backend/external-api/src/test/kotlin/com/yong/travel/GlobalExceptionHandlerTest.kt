package com.yong.travel

import com.yong.travel.auth.security.LoginUserDetails
import com.yong.travel.common.error.GlobalExceptionHandler
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

/** 어떤 실패든 같은 오류 응답 형태로 답하는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `매핑되지 않은 주소는 404 NOT_FOUND 를 반환한다`() {
        mockMvc.perform(get("/api/there-is-no-such-path").with(loggedIn()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `경로 변수 타입이 맞지 않으면 400 VALIDATION_ERROR 를 반환한다`() {
        mockMvc.perform(get("/api/records/not-a-number"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("recordId: 값의 형식이 올바르지 않습니다."))
    }

    @Test
    fun `읽을 수 없는 본문은 400 VALIDATION_ERROR 를 반환한다`() {
        mockMvc.perform(
            put("/api/records/1").with(csrf()).with(loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
    }

    @Test
    fun `지원하지 않는 메서드는 405 를 반환한다`() {
        mockMvc.perform(post("/api/records/1").with(csrf()).with(loggedIn()))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `지원하지 않는 Content-Type 은 415 를 반환한다`() {
        mockMvc.perform(
            put("/api/records/1").with(csrf()).with(loggedIn())
                .contentType(MediaType.TEXT_PLAIN)
                .content("rating=5"),
        )
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    fun `비로그인 상태의 쓰기 요청은 401 UNAUTHENTICATED 를 반환한다`() {
        mockMvc.perform(
            put("/api/records/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"a","category":"FOOD","address":"b","latitude":1.0,"longitude":2.0,"rating":4.5}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `존재하지 않는 기록 조회는 404 RECORD_NOT_FOUND 를 반환한다`() {
        mockMvc.perform(get("/api/records/99999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"))
    }

    // 앱 코드에는 상태만 담긴 예외를 던지는 경로가 없어 핸들러를 직접 부른다.
    @Test
    fun `상태만 담긴 404 는 도메인 코드가 아니라 NOT_FOUND 다`() {
        val response = GlobalExceptionHandler().handleErrorResponseException(ResponseStatusException(HttpStatus.NOT_FOUND))

        assertEquals(404, response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.code)
    }

    @Test
    fun `범용 코드가 없는 4xx 는 상태를 유지하고 BAD_REQUEST 다`() {
        val response = GlobalExceptionHandler().handleErrorResponseException(ResponseStatusException(HttpStatus.NOT_ACCEPTABLE))

        assertEquals(406, response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.code)
    }

    /** 401 로 먼저 막히지 않도록 로그인 상태로 보낸다. */
    private fun loggedIn() = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", 1L), null, emptyList()),
    )
}
