package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.LoginUserDetails
import com.yong.travel.group.service.GroupInviteService
import com.yong.travel.group.service.GroupService
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 사진 파일 응답, 삭제 시 바이너리 정리, 공개 범위에 따른 차단을 확인한다. */
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
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var groupInviteService: GroupInviteService

    @Test
    fun `올린 사진이 응답 url 에서 같은 바이트와 형식으로 나온다`() {
        val owner = newUser()
        val recordId = newRecord(newTrip(owner), owner)
        val bytes = JPEG_HEAD + byteArrayOf(1, 2, 3, 4, 5)

        val photo = photoService.upload(recordId, owner, listOf(jpeg(bytes))).single()

        assertTrue(photo.url.endsWith("/${photo.id}"))
        mockMvc.perform(get(photo.url).with(loggedInAs(owner)))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(content().bytes(bytes))
            .andExpect(header().string("Cache-Control", "max-age=3600, private"))
    }

    @Test
    fun `사진을 지우면 바이너리도 사라져 404 PHOTO_NOT_FOUND 다`() {
        val owner = newUser()
        val recordId = newRecord(newTrip(owner), owner)
        val photo = photoService.upload(recordId, owner, listOf(jpeg(JPEG_HEAD + byteArrayOf(9)))).single()
        // 업로드와 삭제를 별도 요청처럼 다루도록 영속성 컨텍스트를 비운다.
        flush()

        photoService.delete(recordId, photo.id, owner)

        mockMvc.perform(get(photo.url).with(loggedInAs(owner)))
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

        mockMvc.perform(get(gone.url).with(loggedInAs(owner)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"))
        mockMvc.perform(get(alive.url).with(loggedInAs(owner))).andExpect(status().isOk)
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

        photos.forEach { mockMvc.perform(get(it.url).with(loggedInAs(owner))).andExpect(status().isNotFound) }
        mockMvc.perform(get(other.url).with(loggedInAs(owner))).andExpect(status().isOk)
    }

    @Test
    fun `볼 수 없는 여행의 사진은 비로그인·타인 모두 없는 사진과 같은 404 다`() {
        val owner = newUser()
        val stranger = newUser()
        val photo = upload(owner, newTrip(owner))
        flush()

        listOf(get(photo.url), get(photo.url).with(loggedInAs(stranger))).forEach { request ->
            mockMvc.perform(request)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 사진입니다."))
        }
    }

    @Test
    fun `전체 공개 여행의 사진은 비로그인도 받는다`() {
        val owner = newUser()
        val photo = upload(owner, newTrip(owner, TripVisibility.PUBLIC))
        flush()

        mockMvc.perform(get(photo.url)).andExpect(status().isOk)
    }

    @Test
    fun `공유 그룹 멤버는 사진을 받고, 제외되는 즉시 404 가 된다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val tripId = newTrip(owner)
        tripService.changeVisibility(tripId, owner, TripVisibility.GROUP, listOf(groupId))
        val photo = upload(owner, tripId)
        flush()

        mockMvc.perform(get(photo.url).with(loggedInAs(member))).andExpect(status().isOk)

        groupService.removeMember(groupId, member, owner)
        flush()

        mockMvc.perform(get(photo.url).with(loggedInAs(member))).andExpect(status().isNotFound)
    }

    @Test
    fun `공개 범위를 좁히면 커버 사진도 즉시 404 가 된다`() {
        val owner = newUser()
        val tripId = newTrip(owner, TripVisibility.PUBLIC)
        val photo = upload(owner, tripId)
        tripService.changeCover(tripId, owner, photo.id)
        flush()
        mockMvc.perform(get(photo.url)).andExpect(status().isOk)

        tripService.changeVisibility(tripId, owner, TripVisibility.PRIVATE, emptyList())
        flush()

        mockMvc.perform(get(photo.url)).andExpect(status().isNotFound)
        mockMvc.perform(get(photo.url).with(loggedInAs(owner))).andExpect(status().isOk)
    }

    @Test
    fun `UUID 형식이 아닌 id 는 400 VALIDATION_ERROR 다`() {
        mockMvc.perform(get("/api/files/photos/123"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    /** 로그인한 사용자로 요청하도록 principal 을 직접 넣는다. */
    private fun loggedInAs(userId: Long) = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", userId), null, emptyList()),
    )

    private fun upload(ownerId: Long, tripId: Long) =
        photoService.upload(newRecord(tripId, ownerId), ownerId, listOf(jpeg(JPEG_HEAD + byteArrayOf(7)))).single()

    private fun newGroupWith(ownerId: Long, memberId: Long): Long {
        val groupId = requireNotNull(groupService.create(ownerId, "가족", null).id)
        val memberEmail = requireNotNull(userRepository.findById(memberId).orElseThrow().email)
        val invite = groupInviteService.invite(groupId, ownerId, memberEmail)
        groupInviteService.accept(invite.invite.id, memberId)
        return groupId
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

    private fun newTrip(ownerId: Long, visibility: TripVisibility = TripVisibility.PRIVATE): Long =
        tripService.create(
            ownerId,
            TripCreateRequest(
                name = "테스트 여행",
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 8),
                headcount = 2,
                visibility = visibility,
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
        /** 서버가 파일 내용을 확인하므로 JPEG 시그니처로 시작한다. */
        val JPEG_HEAD = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}
