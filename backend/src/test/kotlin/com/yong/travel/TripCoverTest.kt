package com.yong.travel

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.service.PhotoService
import com.yong.travel.record.domain.Category
import com.yong.travel.record.dto.TripChangeRequest
import com.yong.travel.record.dto.TripRecordCreateRequest
import com.yong.travel.record.service.TripRecordService
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.dto.TripCoverUpdateRequest
import com.yong.travel.trip.dto.TripCreateRequest
import com.yong.travel.trip.service.TripService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커버 사진 지정·해제 검증.
 *
 * `cover_photo_id` 에 외래키를 두지 않기로 했으므로(명세 §3), 사진이 여행에서 사라지는 경로마다
 * 커버가 풀리는지 직접 확인해야 한다. 빠뜨리면 없는 사진을 가리키는 커버가 남아 조회가 깨진다.
 */
@SpringBootTest
@Transactional
class TripCoverTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var recordService: TripRecordService
    @Autowired private lateinit var photoService: PhotoService

    @Test
    fun `자기 여행의 사진만 커버로 지정할 수 있다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val photoId = newPhoto(newRecord(tripId, owner), owner)
        flush()

        assertNotNull(tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId)).coverPhotoUrl)

        // 다른 여행의 사진은 404 다. 아예 없는 사진과 구분되지 않아야 한다 (명세 §4.3.2).
        val otherTripId = newTrip(owner)
        assertEquals(
            ErrorCode.PHOTO_NOT_FOUND,
            assertThrows<ApiException> {
                tripService.changeCover(otherTripId, owner, TripCoverUpdateRequest(photoId))
            }.errorCode,
        )
        assertEquals(
            ErrorCode.PHOTO_NOT_FOUND,
            assertThrows<ApiException> {
                tripService.changeCover(tripId, owner, TripCoverUpdateRequest(999_999L))
            }.errorCode,
        )
    }

    @Test
    fun `photoId 가 null 이면 커버 지정이 해제된다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val photoId = newPhoto(newRecord(tripId, owner), owner)
        tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId))
        flush()
        assertNotNull(tripService.get(tripId, owner).coverPhotoUrl)

        tripService.changeCover(tripId, owner, TripCoverUpdateRequest(null))
        flush()

        // 커버가 없으면 null 이다. 서버는 대체 이미지를 고르지 않는다 (명세 §4.3.1).
        assertNull(tripService.get(tripId, owner).coverPhotoUrl)
    }

    @Test
    fun `사진을 지우면 커버 지정도 풀린다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val recordId = newRecord(tripId, owner)
        val photoId = newPhoto(recordId, owner)
        tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId))
        flush()

        photoService.delete(recordId, photoId, owner)
        flush()

        // 외래키가 없으므로 애플리케이션이 풀지 않으면 없는 사진을 가리킨 채 남는다.
        assertNull(tripService.get(tripId, owner).coverPhotoUrl)
    }

    @Test
    fun `커버가 달린 기록을 지우면 커버 지정이 풀린다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val recordId = newRecord(tripId, owner)
        val photoId = newPhoto(recordId, owner)
        tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId))
        flush()

        recordService.delete(recordId, owner)
        flush()

        assertNull(tripService.get(tripId, owner).coverPhotoUrl)
    }

    @Test
    fun `같은 여행의 다른 기록을 지우면 커버는 그대로다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val coverRecordId = newRecord(tripId, owner)
        val otherRecordId = newRecord(tripId, owner)
        newPhoto(otherRecordId, owner)
        tripService.changeCover(tripId, owner, TripCoverUpdateRequest(newPhoto(coverRecordId, owner)))
        flush()

        recordService.delete(otherRecordId, owner)
        flush()

        assertNotNull(tripService.get(tripId, owner).coverPhotoUrl)
    }

    @Test
    fun `기록을 다른 여행으로 옮기면 이전 여행의 커버가 풀린다`() {
        val owner = newUser()
        val fromTripId = newTrip(owner)
        val toTripId = newTrip(owner)
        val recordId = newRecord(fromTripId, owner)
        val photoId = newPhoto(recordId, owner)
        tripService.changeCover(fromTripId, owner, TripCoverUpdateRequest(photoId))
        flush()

        recordService.changeTrip(recordId, owner, TripChangeRequest(toTripId))
        flush()

        // 커버는 자기 여행의 사진만 가리킬 수 있다 (명세 §4.4).
        assertNull(tripService.get(fromTripId, owner).coverPhotoUrl)
        // 옮겨 간 여행이 자동으로 커버를 물려받지는 않는다.
        assertNull(tripService.get(toTripId, owner).coverPhotoUrl)
    }

    @Test
    fun `삭제된 기록의 사진은 커버로 지정할 수 없다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val recordId = newRecord(tripId, owner)
        val photoId = newPhoto(recordId, owner)
        flush()

        recordService.delete(recordId, owner)
        flush()

        // 사진 행은 남아 있지만 지워진 기록의 것이라 커버가 될 수 없다.
        assertEquals(
            ErrorCode.PHOTO_NOT_FOUND,
            assertThrows<ApiException> {
                tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId))
            }.errorCode,
        )
    }

    @Test
    fun `소유자가 아니면 커버를 바꿀 수 없다`() {
        val owner = newUser()
        val stranger = newUser()
        val tripId = newTrip(owner, Visibility.PUBLIC)
        val photoId = newPhoto(newRecord(tripId, owner), owner)
        flush()

        // 공개 여행이라 볼 수는 있다. 그래서 403 이다.
        assertEquals(
            ErrorCode.FORBIDDEN,
            assertThrows<ApiException> {
                tripService.changeCover(tripId, stranger, TripCoverUpdateRequest(photoId))
            }.errorCode,
        )
    }

    @Test
    fun `커버가 지정되면 목록에도 같은 url 이 나온다`() {
        val owner = newUser()
        val tripId = newTrip(owner, Visibility.PUBLIC)
        val photoId = newPhoto(newRecord(tripId, owner), owner)
        val url = tripService.changeCover(tripId, owner, TripCoverUpdateRequest(photoId)).coverPhotoUrl
        flush()

        val fromList = tripService.list(
            com.yong.travel.trip.dto.TripListQuery(com.yong.travel.trip.dto.TripScope.PUBLIC),
            null,
            org.springframework.data.domain.PageRequest.of(0, 10),
        ).content.single { it.id == tripId }
        assertEquals(url, fromList.coverPhotoUrl)
        assertTrue(!url.isNullOrBlank())
    }

    private fun newUser(): Long = requireNotNull(
        userRepository.save(
            User(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = "tester-${System.nanoTime()}@example.com",
                name = "테스터",
            ),
        ).id,
    )

    private fun newTrip(ownerId: Long, visibility: Visibility = Visibility.PRIVATE): Long =
        tripService.create(
            ownerId,
            TripCreateRequest(
                name = "테스트 여행",
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 8),
                headcount = 2,
                visibility = visibility,
            ),
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
        ),
    ).id

    private fun newPhoto(recordId: Long, ownerId: Long): Long = photoService.upload(
        recordId,
        ownerId,
        listOf(
            MockMultipartFile(
                "files",
                "photo-${System.nanoTime()}.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                byteArrayOf(1, 2, 3),
            ),
        ),
    ).single().id

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
