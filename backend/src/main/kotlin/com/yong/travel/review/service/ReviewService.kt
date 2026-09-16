package com.yong.travel.review.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.place.repository.PlaceRepository
import com.yong.travel.review.domain.Review
import com.yong.travel.review.dto.ReviewRequest
import com.yong.travel.review.dto.ReviewResponse
import com.yong.travel.review.repository.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface ReviewService {
    fun list(placeId: Long, pageable: Pageable): PageResponse<ReviewResponse>

    fun getMine(placeId: Long, userId: Long): ReviewResponse?

    /** 사용자당 여행지 1건만 허용하므로 등록/수정을 upsert 로 처리한다. */
    fun upsert(placeId: Long, userId: Long, request: ReviewRequest): ReviewResponse

    fun delete(placeId: Long, userId: Long)
}

@Service
@Transactional(readOnly = true)
class ReviewServiceImpl(
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository,
) : ReviewService {

    override fun list(placeId: Long, pageable: Pageable): PageResponse<ReviewResponse> {
        val page = reviewRepository.findByPlaceId(
            placeId,
            PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return PageResponse.of(page.map { it.toResponse() })
    }

    override fun getMine(placeId: Long, userId: Long): ReviewResponse? =
        reviewRepository.findByPlaceIdAndUserId(placeId, userId)?.toResponse()

    @Transactional
    override fun upsert(placeId: Long, userId: Long, request: ReviewRequest): ReviewResponse {
        val place = placeRepository.findById(placeId).orElseThrow { ApiException(ErrorCode.PLACE_NOT_FOUND) }

        // (place_id, user_id) 유니크 제약이 살아 있으므로 soft delete 된 행이 있으면 새로 만들지 않고 되살린다.
        val existing = reviewRepository.findAnyByPlaceIdAndUserId(placeId, userId)
        if (existing != null) {
            existing.score = request.score
            existing.content = request.content
            existing.restore()
            // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
            return reviewRepository.saveAndFlush(existing).toResponse()
        }

        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val review = Review(place = place, user = user, score = request.score, content = request.content)
        return reviewRepository.save(review).toResponse()
    }

    /** soft delete. 같은 사용자가 다시 리뷰를 남기면 `upsert` 가 이 행을 되살린다. */
    @Transactional
    override fun delete(placeId: Long, userId: Long) {
        val review = reviewRepository.findByPlaceIdAndUserId(placeId, userId)
            ?: throw ApiException(ErrorCode.REVIEW_NOT_FOUND)
        review.softDelete()
    }

    private fun Review.toResponse() =
        ReviewResponse(
            id = requireNotNull(id),
            author = user.toResponse(),
            score = score,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
