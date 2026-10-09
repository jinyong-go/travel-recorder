package com.yong.travel.record.presentation

import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.TripRecordCreateCommand
import com.yong.travel.record.domain.TripRecordUpdateCommand
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlin.math.floor

data class TripRecordCreateRequest(
    /**
     * 소속 여행. 필수이며, 요청자가 소유하지 않은 여행이면 404 TRIP_NOT_FOUND 다.
     * 기록은 여행 없이 존재할 수 없어 기본값을 두지 않는다.
     */
    @field:NotNull
    val tripId: Long,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:NotNull
    val category: Category,

    /** 기록당 10개, 각 20자 이하. 개수는 요청에 담긴 그대로 센다. 길이는 [isTagLengthValid]. */
    @field:Size(max = 10)
    val tags: List<String> = emptyList(),

    @field:NotBlank
    @field:Size(max = 255)
    val address: String,

    @field:Size(max = 255)
    val roadAddress: String? = null,

    /**
     * 화면에서 링크로 열리는 값이라 웹 주소만 받는다. `javascript:` 같은 스킴을
     * 받아 두면 링크를 누른 사람의 브라우저에서 스크립트가 실행될 수 있다.
     */
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

    fun toCommand() = TripRecordCreateCommand(
        tripId, name, category, tags, address, roadAddress, externalLink, latitude, longitude, rating, memo,
    )
}

data class TripRecordUpdateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:NotNull
    val category: Category,

    /** 기록당 10개, 각 20자 이하. 개수는 요청에 담긴 그대로 센다. 길이는 [isTagLengthValid]. */
    @field:Size(max = 10)
    val tags: List<String> = emptyList(),

    @field:NotBlank
    @field:Size(max = 255)
    val address: String,

    @field:Size(max = 255)
    val roadAddress: String? = null,

    /**
     * 화면에서 링크로 열리는 값이라 웹 주소만 받는다. `javascript:` 같은 스킴을
     * 받아 두면 링크를 누른 사람의 브라우저에서 스크립트가 실행될 수 있다.
     */
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

    fun toCommand() = TripRecordUpdateCommand(
        name, category, tags, address, roadAddress, externalLink, latitude, longitude, rating, memo,
    )
}

/**
 * 기록을 다른 여행으로 옮긴다. 대상 여행은 요청자가 소유한 여행이어야 한다.
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

/**
 * 태그 하나의 길이 상한 검증.
 *
 * 원소 제약(`List<@Size String>`)으로 쓰지 않는 이유: Kotlin 이 타입 인자의 애노테이션을 바이트코드에
 * 남기지 않아 Bean Validation 이 보지 못한다. 그래서 다른 값 규칙처럼 `@AssertTrue` 로 검사한다.
 */
private fun areTagsWithinLength(tags: List<String>): Boolean = tags.all { it.length <= MAX_TAG_LENGTH }

private const val MAX_TAG_LENGTH = 20
