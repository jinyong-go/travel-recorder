package com.yong.travel.review.dto

import com.yong.travel.auth.dto.UserResponse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.time.Instant
import kotlin.math.floor

data class ReviewRequest(
    @field:DecimalMin("0.5")
    @field:DecimalMax("5.0")
    val score: Double,

    /** 별점만 남기는 것도 허용하므로 선택 입력이다. */
    @field:Size(max = 1000)
    val content: String? = null,
) {
    /**
     * 0.5 단위 검증. 0.5 배수는 이진 부동소수점으로 정확히 표현되므로 `score * 2` 의
     * 정수 여부만 보면 되고, 별도의 허용 오차가 필요 없다.
     */
    @get:AssertTrue(message = "별점은 0.5점 단위로만 입력할 수 있습니다.")
    val isHalfPointStep: Boolean
        get() = score * 2 == floor(score * 2)
}

data class ReviewResponse(
    val id: Long,
    val author: UserResponse,
    val score: Double,
    val content: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
