package com.yong.travel.trip.record.presentation

import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.GeoPoint
import com.yong.travel.trip.record.domain.TripRecordCreateCommand
import com.yong.travel.trip.record.domain.TripRecordUpdateCommand
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlin.math.floor

/** 기록 등록 요청. */
data class TripRecordCreateRequest(
    /** 소속 여행. 요청자가 소유한 여행이어야 한다. */
    @field:NotNull
    val tripId: Long,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:NotNull
    val category: Category,

    /** 최대 10개, 각 20자 이하. */
    @field:Size(max = 10)
    val tags: List<String> = emptyList(),

    @field:NotBlank
    @field:Size(max = 255)
    val address: String,

    @field:Size(max = 255)
    val roadAddress: String? = null,

    /** 링크로 열리는 값이라 `javascript:` 같은 스킴이 실행되지 않도록 웹 주소만 받는다. */
    @field:Size(max = 255)
    @field:Pattern(regexp = "^https?://\\S+$", flags = [Pattern.Flag.CASE_INSENSITIVE])
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

    @get:AssertTrue(message = "태그는 20자 이하여야 합니다.")
    val isTagLengthValid: Boolean
        get() = areTagsWithinLength(tags)

    @get:AssertTrue(message = COORDINATE_RANGE_MESSAGE)
    val isValidCoordinate: Boolean
        get() = GeoPoint.isValid(latitude, longitude)

    /** 요청 → 서비스 입력. */
    fun toCommand() = TripRecordCreateCommand(
        tripId, name, category, tags, address, roadAddress, externalLink, latitude, longitude, rating, memo,
    )
}

/** 기록 수정 요청. 필드 규칙은 [TripRecordCreateRequest] 와 같고, 여행 이동은 따로 한다. */
data class TripRecordUpdateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:NotNull
    val category: Category,

    @field:Size(max = 10)
    val tags: List<String> = emptyList(),

    @field:NotBlank
    @field:Size(max = 255)
    val address: String,

    @field:Size(max = 255)
    val roadAddress: String? = null,

    @field:Size(max = 255)
    @field:Pattern(regexp = "^https?://\\S+$", flags = [Pattern.Flag.CASE_INSENSITIVE])
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

    @get:AssertTrue(message = "태그는 20자 이하여야 합니다.")
    val isTagLengthValid: Boolean
        get() = areTagsWithinLength(tags)

    @get:AssertTrue(message = COORDINATE_RANGE_MESSAGE)
    val isValidCoordinate: Boolean
        get() = GeoPoint.isValid(latitude, longitude)

    /** 요청 → 서비스 입력. */
    fun toCommand() = TripRecordUpdateCommand(
        name, category, tags, address, roadAddress, externalLink, latitude, longitude, rating, memo,
    )
}

/** 기록을 옮길 여행. 옮기면 공개 범위도 새 여행을 따른다. */
data class TripChangeRequest(
    @field:NotNull
    val tripId: Long,
)

/** 0.5 단위인지 확인한다. 0.5 배수는 부동소수점으로 정확히 표현되어 오차를 고려하지 않는다. */
private fun isHalfPoint(value: Double): Boolean = value * 2 == floor(value * 2)

/**
 * 태그마다 길이 상한을 넘지 않는지 확인한다.
 * Kotlin 에서는 `List<@Size String>` 이 검증되지 않아 직접 확인한다.
 */
private fun areTagsWithinLength(tags: List<String>): Boolean = tags.all { it.length <= MAX_TAG_LENGTH }

private const val MAX_TAG_LENGTH = 20

private const val COORDINATE_RANGE_MESSAGE = "좌표는 위도 -90~90, 경도 -180~180 범위여야 합니다."
