package com.yong.travel

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 어떤 경로로 실패하든 `ErrorResponse`(code/message/status) 한 가지 형태로 응답하는지 확인한다.
 * 컨트롤러를 거치지 않는 실패(주소 없음, 메서드 불일치 등)까지 포함한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `매핑되지 않은 주소는 404 NOT_FOUND 를 반환한다`() {
        mockMvc.perform(get("/api/there-is-no-such-path"))
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
            put("/api/records/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
    }

    @Test
    fun `지원하지 않는 메서드는 405 를 반환한다`() {
        mockMvc.perform(post("/api/records/1").with(csrf()))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `지원하지 않는 Content-Type 은 415 를 반환한다`() {
        mockMvc.perform(
            put("/api/records/1").with(csrf())
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
}
