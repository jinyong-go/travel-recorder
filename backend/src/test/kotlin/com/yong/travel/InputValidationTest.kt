package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.service.PhotoService
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.RecordListQuery
import com.yong.travel.record.domain.TripRecordCreateCommand
import com.yong.travel.record.presentation.TripRecordCreateRequest
import com.yong.travel.record.service.TripRecordService
import com.yong.travel.tag.service.TagService
import com.yong.travel.trip.domain.TripCreateCommand
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.service.TripService
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 입력 상한과 형식 검증 — 기록 요청 값, 업로드 파일, 검색어, 태그 자동완성, 장소 검색 인증.
 *
 * 받아서는 안 되는 값이 조용히 저장되는 것을 막는 규칙들이라, 위반이 실제로 거부되는지와
 * 정상 값이 통과하는지를 함께 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InputValidationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var validator: Validator
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var recordService: TripRecordService
    @Autowired private lateinit var photoService: PhotoService
    @Autowired private lateinit var tagService: TagService

    @Test
    fun `원본 링크는 웹 주소만 받는다`() {
        assertTrue(violationsOf(request(externalLink = "https://map.naver.com/p/12345")).isEmpty())
        assertTrue(violationsOf(request(externalLink = "HTTP://example.com")).isEmpty())
        assertEquals(setOf("externalLink"), violationsOf(request(externalLink = "javascript:alert(1)")))
        assertEquals(setOf("externalLink"), violationsOf(request(externalLink = "data:text/html,x")))
    }

    @Test
    fun `태그는 10개까지, 각 20자까지다`() {
        assertTrue(violationsOf(request(tags = List(10) { "태그$it" })).isEmpty())
        assertEquals(setOf("tags"), violationsOf(request(tags = List(11) { "태그$it" })))
        assertTrue(violationsOf(request(tags = listOf("가".repeat(20)))).isEmpty())
        assertEquals(setOf("tagLengthValid"), violationsOf(request(tags = listOf("짧은태그", "가".repeat(21)))))
    }

    @Test
    fun `장소 정보는 각각 255자까지다`() {
        assertTrue(violationsOf(request(name = "가".repeat(255))).isEmpty())
        assertEquals(setOf("name"), violationsOf(request(name = "가".repeat(256))))
        assertEquals(setOf("address"), violationsOf(request(address = "가".repeat(256))))
    }

    @Test
    fun `선언한 형식과 시그니처가 다른 파일은 INVALID_FILE 이다`() {
        val owner = newUser()
        val recordId = newRecord(owner, newTrip(owner), emptyList())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        listOf(
            MockMultipartFile("files", "a.jpg", MediaType.IMAGE_JPEG_VALUE, png),
            MockMultipartFile("files", "a.jpg", MediaType.IMAGE_JPEG_VALUE, "<html>".toByteArray()),
        ).forEach { file ->
            val e = assertThrows<ApiException> { photoService.upload(recordId, owner, listOf(file)) }
            assertEquals(ErrorCode.INVALID_FILE, e.errorCode)
        }
        // 선언과 내용이 맞으면 통과한다.
        photoService.upload(recordId, owner, listOf(MockMultipartFile("files", "a.png", MediaType.IMAGE_PNG_VALUE, png)))
    }

    @Test
    fun `검색어의 퍼센트와 밑줄은 글자 그대로 찾는다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        newRecord(owner, tripId, emptyList(), name = "할인 50% 매장")
        newRecord(owner, tripId, emptyList(), name = "평범한 매장")
        newRecord(owner, tripId, emptyList(), name = "a_b 카페")
        newRecord(owner, tripId, emptyList(), name = "axb 카페")

        fun names(keyword: String) = recordService
            .list(RecordListQuery(scope = null, tripId = tripId, keyword = keyword), owner, PageRequest.of(0, 10))
            .content.map { it.name }

        assertEquals(listOf("할인 50% 매장"), names("%"))
        assertEquals(listOf("a_b 카페"), names("a_b"))
    }

    @Test
    fun `태그 자동완성은 20건까지다`() {
        val owner = newUser()
        // 서비스 입력(Command)에는 요청 DTO 의 개수 상한이 없으므로 한 기록에 25개를 붙여 둔다.
        newRecord(owner, newTrip(owner), List(25) { "자동완성%02d".format(it) })

        val tags = tagService.search("자동완성", owner)
        assertEquals(20, tags.size)
        assertEquals("자동완성00", tags.first().name)
    }

    @Test
    fun `장소 검색은 로그인이 필요하다`() {
        mockMvc.perform(get("/api/places/search").param("keyword", "카페"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun violationsOf(request: TripRecordCreateRequest): Set<String> =
        validator.validate(request).map { it.propertyPath.toString() }.toSet()

    private fun request(
        name: String = "성산일출봉",
        address: String = "제주특별자치도 서귀포시",
        externalLink: String? = null,
        tags: List<String> = emptyList(),
    ) = TripRecordCreateRequest(
        tripId = 1,
        name = name,
        category = Category.SIGHT,
        tags = tags,
        address = address,
        externalLink = externalLink,
        latitude = 33.45,
        longitude = 126.94,
        rating = 4.5,
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

    private fun newTrip(ownerId: Long): Long =
        tripService.create(
            ownerId,
            TripCreateCommand("여행", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 2, null, null, TripVisibility.PRIVATE, emptyList()),
        ).id

    private fun newRecord(ownerId: Long, tripId: Long, tags: List<String>, name: String = "기록"): Long =
        recordService.create(
            ownerId,
            TripRecordCreateCommand(tripId, name, Category.FOOD, tags, "주소", null, null, 37.5, 127.0, 4.0, null),
        ).id
}
