package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.presentation.TripCreateRequest
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.GeoPoint
import com.yong.travel.trip.record.domain.RecordListQuery
import com.yong.travel.trip.record.domain.RecordSort
import com.yong.travel.trip.record.presentation.TripRecordCreateRequest
import com.yong.travel.trip.record.service.TripRecordService
import com.yong.travel.trip.service.TripService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 기록 목록의 거리순 정렬 검증.
 *
 * 정렬은 DB 가 기준 위치로 하므로 페이지를 넘어서도 가까운 순이 이어져야 한다. 페이지 안에서만
 * 다시 정렬하면 두 번째 페이지에 첫 페이지보다 가까운 기록이 섞인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecordDistanceSortTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var recordService: TripRecordService

    @Test
    fun `거리순은 페이지를 넘어 가까운 순으로 이어진다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        // 등록 순서와 거리 순서를 일부러 뒤집는다. 최신순으로 자른 뒤 다시 정렬하면 이 테스트가 깨진다.
        (12 downTo 1).forEach { newRecord(tripId, owner, latitude = BASE_LAT + it * 0.01) }
        flush()

        fun page(n: Int) = recordService
            .list(RecordListQuery(scope = null, tripId = tripId, sort = RecordSort.DISTANCE, origin = GeoPoint(BASE_LAT, BASE_LNG)), owner, n)
        val first = page(0)
        val second = page(1)

        assertEquals(12L, first.totalElements)
        val distances = (first.content + second.content).map { requireNotNull(it.distanceKm) }
        assertEquals(distances.sorted(), distances)
        assertTrue(first.content.last().distanceKm!! < second.content.first().distanceKm!!)
    }

    @Test
    fun `거리순에 기준 좌표가 없으면 400 VALIDATION_ERROR 다`() {
        mockMvc.perform(get("/api/records?scope=PUBLIC&sort=DISTANCE"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        mockMvc.perform(get("/api/records?scope=PUBLIC&sort=DISTANCE&lat=$BASE_LAT"))
            .andExpect(status().isBadRequest)
    }

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

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
                visibility = TripVisibility.PUBLIC,
            ).toCommand(),
        ).id

    private fun newRecord(tripId: Long, ownerId: Long, latitude: Double): Long = recordService.create(
        ownerId,
        TripRecordCreateRequest(
            tripId = tripId,
            name = "테스트 기록",
            category = Category.FOOD,
            address = "제주시 어딘가",
            latitude = latitude,
            longitude = BASE_LNG,
            rating = 4.5,
        ).toCommand(),
    ).id

    private companion object {
        const val BASE_LAT = 33.5
        const val BASE_LNG = 126.5
    }
}
