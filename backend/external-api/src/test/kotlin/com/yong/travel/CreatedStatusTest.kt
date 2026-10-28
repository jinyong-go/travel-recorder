package com.yong.travel

import com.jayway.jsonpath.JsonPath
import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.LoginUserDetails
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/** 생성 요청이 201 과 만들어진 리소스를 돌려주는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreatedStatusTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `여행·기록·그룹 생성과 사진 업로드는 201 이다`() {
        val owner = newUser()

        val tripId = createdId(
            post("/api/trips").content(
                """{"name":"제주","startDate":"2026-09-05","endDate":"2026-09-08","headcount":2}""",
            ),
            owner,
        )
        val recordId = createdId(
            post("/api/records").content(
                """{"tripId":$tripId,"name":"성산일출봉","category":"SIGHT","address":"제주",""" +
                    """"latitude":33.45,"longitude":126.94,"rating":4.5}""",
            ),
            owner,
        )
        createdId(post("/api/groups").content("""{"name":"가족"}"""), owner)

        val photo = MockMultipartFile("files", "a.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_HEAD)
        mockMvc.perform(multipart("/api/records/$recordId/photos").file(photo).with(csrf()).with(loggedInAs(owner)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].url").isNotEmpty)
    }

    /** JSON 생성 요청을 보내 201 을 확인하고 응답의 id 를 돌려준다. */
    private fun createdId(
        request: MockHttpServletRequestBuilder,
        userId: Long,
    ): Long {
        val body = mockMvc.perform(
            request.contentType(MediaType.APPLICATION_JSON).with(csrf()).with(loggedInAs(userId)),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return JsonPath.read<Number>(body, "$.id").toLong()
    }

    /** 로그인한 사용자로 요청하도록 principal 을 직접 넣는다. */
    private fun loggedInAs(userId: Long) = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", userId), null, emptyList()),
    )

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

    private companion object {
        /** 서버가 파일 내용을 확인하므로 JPEG 시그니처로 시작한다. */
        val JPEG_HEAD = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}
