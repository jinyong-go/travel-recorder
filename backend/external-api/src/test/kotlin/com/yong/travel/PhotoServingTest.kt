package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.photo.service.PhotoService
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.presentation.TripRecordCreateRequest
import com.yong.travel.trip.record.service.TripRecordService
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.presentation.TripCreateRequest
import com.yong.travel.trip.service.TripService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DB 에 저장한 사진 바이너리의 서빙 검증.
 *
 * 응답의 `url` 을 그대로 요청해 올린 바이트가 그대로 나오는지, 사진을 지우면 바이너리도 함께
 * 사라지는지(ON DELETE CASCADE), 기록·여행을 지우면 사진 행은 남고 바이너리만 사라지는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhotoServingTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var recordService: TripRecordService
    @Autowired private lateinit var photoService: PhotoService

    @Test
    fun `올린 사진이 응답 url 에서 같은 바이트와 형식으로 나온다`() {
        val owner = newUser()
        val recordId = newRecord(newTrip(owner), owner)
        val bytes = JPEG_HEAD + byteArrayOf(1, 2, 3, 4, 5)

        val photo = photoService.upload(recordId, owner, listOf(jpeg(bytes))).single()

        assertTrue(photo.url.endsWith("/${photo.id}"))
        mockMvc.perform(get(photo.url))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(content().bytes(bytes))
    }

    @Test
    fun `사진을 지우면 바이너리도 사라져 404 PHOTO_NOT_FOUND 다`() {
        val owner = newUser()
        val recordId = newRecord(newTrip(owner), owner)
        val photo = photoService.upload(recordId, owner, listOf(jpeg(JPEG_HEAD + byteArrayOf(9)))).single()
        // 업로드와 삭제는 실제로 별도 요청이다. 같은 컨텍스트에 바이너리 엔티티가 남아 있으면
        // 지워진 사진을 참조한 채 flush 되므로 요청 경계를 흉내 낸다.
        flush()

        photoService.delete(recordId, photo.id, owner)

        mockMvc.perform(get(photo.url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"))
    }

    @Test
    fun `기록을 지우면 그 기록 사진만 404 가 되고 사진 행은 남는다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val deleted = newRecord(tripId, owner)
        val kept = newRecord(tripId, owner)
        val gone = photoService.upload(deleted, owner, listOf(jpeg(JPEG_HEAD + byteArrayOf(1)))).single()
        val alive = photoService.upload(kept, owner, listOf(jpeg(JPEG_HEAD + byteArrayOf(2)))).single()
        flush()

        recordService.delete(deleted, owner)
        flush()

        mockMvc.perform(get(gone.url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"))
        mockMvc.perform(get(alive.url)).andExpect(status().isOk)
        assertEquals(1L, count("SELECT count(*) FROM photos WHERE id = '${gone.id}'"))
    }

    @Test
    fun `여행을 지우면 하위 기록 사진이 모두 404 가 된다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val photos = listOf(newRecord(tripId, owner), newRecord(tripId, owner)).map {
            photoService.upload(it, owner, listOf(jpeg(JPEG_HEAD + byteArrayOf(3)))).single()
        }
        val otherTrip = newTrip(owner)
        val other = photoService.upload(newRecord(otherTrip, owner), owner, listOf(jpeg(JPEG_HEAD))).single()
        flush()

        tripService.delete(tripId, owner)
        flush()

        photos.forEach { mockMvc.perform(get(it.url)).andExpect(status().isNotFound) }
        mockMvc.perform(get(other.url)).andExpect(status().isOk)
    }

    @Test
    fun `UUID 형식이 아닌 id 는 400 VALIDATION_ERROR 다`() {
        mockMvc.perform(get("/api/files/photos/123"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    private fun count(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun jpeg(bytes: ByteArray) =
        MockMultipartFile("files", "photo-${System.nanoTime()}.jpg", MediaType.IMAGE_JPEG_VALUE, bytes)

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
            TripCreateRequest(
                name = "테스트 여행",
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 8),
                headcount = 2,
                visibility = TripVisibility.PRIVATE,
            ).toCommand(),
        ).id

    private fun newRecord(tripId: Long, ownerId: Long): Long = recordService.create(
        ownerId,
        TripRecordCreateRequest(
            tripId = tripId,
            name = "테스트 기록",
            category = Category.FOOD,
            address = "서울시 어딘가",
            latitude = 37.5,
            longitude = 127.0,
            rating = 4.5,
        ).toCommand(),
    ).id

    private companion object {
        /** 서버가 파일 앞부분의 시그니처를 확인하므로 JPEG 시그니처로 시작해야 한다. */
        val JPEG_HEAD = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}
