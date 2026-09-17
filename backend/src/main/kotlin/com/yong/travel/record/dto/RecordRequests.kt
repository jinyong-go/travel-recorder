package com.yong.travel.record.dto

import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.Visibility
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlin.math.floor

data class VisitRecordCreateRequest(
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

    /** 생략하면 PRIVATE. 명시적으로 넓히지 않는 한 공개되지 않는다. */
    val visibility: Visibility = Visibility.PRIVATE,

    /** visibility = GROUP 일 때만 의미가 있다. 그 외 값이면 무시된다. */
    val groupIds: List<Long> = emptyList(),
) {
    @get:AssertTrue(message = "평점은 0.5점 단위로만 입력할 수 있습니다.")
    val isHalfPointStep: Boolean
        get() = isHalfPoint(rating)
}

data class VisitRecordUpdateRequest(
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

    val visibility: Visibility = Visibility.PRIVATE,

    val groupIds: List<Long> = emptyList(),
) {
    @get:AssertTrue(message = "평점은 0.5점 단위로만 입력할 수 있습니다.")
    val isHalfPointStep: Boolean
        get() = isHalfPoint(rating)
}

/** 공개 범위만 바꾼다. 공유 그룹 목록은 전체 교체이며, 빠진 그룹의 공유는 해제된다. */
data class VisibilityUpdateRequest(
    @field:NotNull
    val visibility: Visibility,

    val groupIds: List<Long> = emptyList(),
)

/**
 * 0.5 단위 검증. 0.5 배수는 이진 부동소수점으로 정확히 표현되므로
 * `rating * 2` 의 정수 여부만 보면 되고, 별도의 허용 오차가 필요 없다.
 */
private fun isHalfPoint(value: Double): Boolean = value * 2 == floor(value * 2)
