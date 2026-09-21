package com.yong.travel.record.dto

import com.yong.travel.record.domain.Category
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlin.math.floor

data class TripRecordCreateRequest(
    /**
     * 소속 여행. 필수이며, 요청자가 소유하지 않은 여행이면 404 TRIP_NOT_FOUND 다 (명세 §4.4).
     * 기록은 여행 없이 존재할 수 없어 기본값을 두지 않는다.
     */
    @field:NotNull
    val tripId: Long,

    @field:NotBlank
    val name: String,

    @field:NotNull
    val category: Category,

    val tags: List<String> = emptyList(),

    @field:NotBlank
    val address: String,

    val roadAddress: String? = null,

    val externalLink: String? = null,

    @field:NotNull
    val latitude: Double,

    @field:NotNull
    val longitude: Double,

    @field:DecimalMin("0.5")
    @field:DecimalMax("5.0")
    val rating: Double,

    @field:Size(max = 1000)
    val memo: String? = null,
) {
    @get:AssertTrue(message = "평점은 0.5점 단위로만 입력할 수 있습니다.")
    val isHalfPointStep: Boolean
        get() = isHalfPoint(rating)
}

data class TripRecordUpdateRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val category: Category,

    val tags: List<String> = emptyList(),

    @field:NotBlank
    val address: String,

    val roadAddress: String? = null,

    val externalLink: String? = null,

    @field:NotNull
    val latitude: Double,

    @field:NotNull
    val longitude: Double,

    @field:DecimalMin("0.5")
    @field:DecimalMax("5.0")
    val rating: Double,

    @field:Size(max = 1000)
    val memo: String? = null,
) {
    @get:AssertTrue(message = "평점은 0.5점 단위로만 입력할 수 있습니다.")
    val isHalfPointStep: Boolean
        get() = isHalfPoint(rating)
}

/**
 * 기록을 다른 여행으로 옮긴다. 대상 여행은 요청자가 소유한 여행이어야 한다 (명세 §4.4).
 *
 * 옮기는 즉시 그 기록의 공개 범위는 새 여행의 것이 된다 — 넓히는 이동일 수 있다.
 */
data class TripChangeRequest(
    @field:NotNull
    val tripId: Long,
)

/**
 * 0.5 단위 검증. 0.5 배수는 이진 부동소수점으로 정확히 표현되므로
 * `rating * 2` 의 정수 여부만 보면 되고, 별도의 허용 오차가 필요 없다.
 */
private fun isHalfPoint(value: Double): Boolean = value * 2 == floor(value * 2)
