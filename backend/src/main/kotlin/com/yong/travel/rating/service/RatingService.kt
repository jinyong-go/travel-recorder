package com.yong.travel.rating.service

import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.place.repository.PlaceRepository
import com.yong.travel.rating.domain.Rating
import com.yong.travel.rating.dto.RatingRequest
import com.yong.travel.rating.dto.RatingResponse
import com.yong.travel.rating.repository.RatingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface RatingService {
    fun getMine(placeId: Long, userId: Long): RatingResponse?

    /** 사용자당 여행지 1건만 허용하므로 등록/수정을 upsert 로 처리한다. */
    fun upsert(placeId: Long, userId: Long, request: RatingRequest): RatingResponse

    fun delete(placeId: Long, userId: Long)
}

@Service
@Transactional(readOnly = true)
class RatingServiceImpl(
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val ratingRepository: RatingRepository,
) : RatingService {

    override fun getMine(placeId: Long, userId: Long): RatingResponse? =
        ratingRepository.findByPlaceIdAndUserId(placeId, userId)?.toResponse()

    @Transactional
    override fun upsert(placeId: Long, userId: Long, request: RatingRequest): RatingResponse {
        val existing = ratingRepository.findByPlaceIdAndUserId(placeId, userId)
        if (existing != null) {
            existing.score = request.score
            return ratingRepository.save(existing).toResponse()
        }

        val place = placeRepository.findById(placeId).orElseThrow { ApiException(ErrorCode.PLACE_NOT_FOUND) }
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        return ratingRepository.save(Rating(place = place, user = user, score = request.score)).toResponse()
    }

    @Transactional
    override fun delete(placeId: Long, userId: Long) {
        val rating = ratingRepository.findByPlaceIdAndUserId(placeId, userId) ?: return
        ratingRepository.delete(rating)
    }

    private fun Rating.toResponse() = RatingResponse(score = score, updatedAt = updatedAt)
}
