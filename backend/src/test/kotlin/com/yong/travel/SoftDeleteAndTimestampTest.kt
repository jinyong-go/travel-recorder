package com.yong.travel

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.place.domain.Category
import com.yong.travel.place.dto.PlaceCreateRequest
import com.yong.travel.place.dto.PlaceListQuery
import com.yong.travel.place.service.PlaceService
import com.yong.travel.review.dto.ReviewRequest
import com.yong.travel.review.service.ReviewService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 리뷰(별점 + 코멘트)의 soft delete (`deleted_at`) 와 수정 시각 (`updated_at`) 동작 검증.
 * 1차 캐시 때문에 변경 직후 조회가 DB 를 타지 않으므로 검증 전에 flush/clear 로 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class SoftDeleteAndTimestampTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var placeService: PlaceService
    @Autowired private lateinit var reviewService: ReviewService

    @Test
    fun `여행지를 삭제하면 목록·상세에서 사라지고 종속 리뷰도 함께 숨겨진다`() {
        val owner = newUser()
        val placeId = newPlace(owner)
        reviewService.upsert(placeId, owner, ReviewRequest(score = 5.0, content = "좋아요"))
        flush()

        placeService.delete(placeId, owner)
        flush()

        assertThrows<ApiException> { placeService.get(placeId) }
        assertTrue(placeService.list(PlaceListQuery(), PageRequest.of(0, 10)).content.none { it.id == placeId })
        assertEquals(1L, count("select count(*) from places"), "행 자체는 남아 있어야 한다")
        assertEquals(0L, count("select count(*) from reviews where place_id = $placeId and deleted_at is null"))
    }

    @Test
    fun `리뷰는 별점과 코멘트를 한 건으로 저장하고 사용자당 1건만 유지한다`() {
        val owner = newUser()
        val placeId = newPlace(owner)

        val created = reviewService.upsert(placeId, owner, ReviewRequest(score = 5.0, content = "좋아요"))
        flush()
        val updated = reviewService.upsert(placeId, owner, ReviewRequest(score = 3.5, content = "다시 보니 별로"))
        flush()

        assertEquals(created.id, updated.id, "같은 사용자의 재등록은 새 행을 만들지 않는다")
        assertEquals(3.5, updated.score)
        assertEquals("다시 보니 별로", updated.content)
        assertTrue(updated.updatedAt > created.updatedAt, "응답의 updatedAt 이 갱신된 값이어야 한다")
        assertEquals(1L, count("select count(*) from reviews where place_id = $placeId"))
        assertEquals(1, placeService.get(placeId).rating.count)
        assertEquals(3.5, placeService.get(placeId).rating.average)
    }

    @Test
    fun `코멘트 없이 별점만 남길 수 있다`() {
        val owner = newUser()
        val placeId = newPlace(owner)

        val review = reviewService.upsert(placeId, owner, ReviewRequest(score = 4.5))
        flush()

        assertNull(review.content)
        assertEquals(4.5, reviewService.getMine(placeId, owner)?.score)
    }

    @Test
    fun `리뷰를 삭제한 뒤 다시 남기면 유니크 제약을 깨지 않고 기존 행이 되살아난다`() {
        val owner = newUser()
        val placeId = newPlace(owner)
        val created = reviewService.upsert(placeId, owner, ReviewRequest(score = 5.0, content = "좋아요"))
        flush()

        reviewService.delete(placeId, owner)
        flush()
        assertNull(reviewService.getMine(placeId, owner))
        assertTrue(reviewService.list(placeId, PageRequest.of(0, 10)).content.isEmpty())
        assertEquals(1L, count("select count(*) from reviews where id = ${created.id}"), "행은 남아 있어야 한다")

        val revived = reviewService.upsert(placeId, owner, ReviewRequest(score = 3.5, content = "다시 방문"))
        flush()

        assertEquals(created.id, revived.id)
        assertEquals(3.5, revived.score)
        assertEquals(1L, count("select count(*) from reviews where place_id = $placeId"), "새 행을 만들지 않아야 한다")
    }

    @Test
    fun `리뷰가 없는 사용자의 삭제 요청은 404 가 된다`() {
        val owner = newUser()
        val placeId = newPlace(owner)
        flush()

        assertThrows<ApiException> { reviewService.delete(placeId, owner) }
    }

    @Test
    fun `여러 사용자가 남긴 별점은 평균으로 집계되며 0_5 단위가 아닐 수 있다`() {
        val owner = newUser()
        val other = newUser()
        val placeId = newPlace(owner)

        reviewService.upsert(placeId, owner, ReviewRequest(score = 5.0, content = "최고"))
        reviewService.upsert(placeId, other, ReviewRequest(score = 4.5))
        flush()

        val summary = placeService.get(placeId).rating
        assertEquals(2, summary.count)
        // 개별 별점은 0.5 단위지만 평균은 그렇지 않다.
        assertEquals(4.75, summary.average)
        assertEquals(2, reviewService.list(placeId, PageRequest.of(0, 10)).content.size)
    }

    private fun newUser(): Long = requireNotNull(
        userRepository.save(
            User(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = "tester@example.com",
                name = "테스터",
            ),
        ).id,
    )

    private fun newPlace(ownerId: Long): Long = placeService.create(
        ownerId,
        PlaceCreateRequest(
            name = "테스트 여행지",
            category = Category.FOOD,
            address = "서울시 어딘가",
            latitude = 37.5,
            longitude = 127.0,
        ),
    ).id

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun count(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()
}
