package com.yong.travel.trip.dto

import com.yong.travel.trip.domain.TripCreateCommand
import com.yong.travel.trip.domain.TripUpdateCommand
import com.yong.travel.trip.domain.Visibility
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 여행 생성.
 *
 * `coverPhotoId` 는 담을 수 없다 — 지정할 사진이 아직 존재할 수 없다 (명세 §4.3.2).
 */
data class TripCreateRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:NotNull
    val startDate: LocalDate,

    @field:NotNull
    val endDate: LocalDate,

    /** 본인을 포함한 사람 수. 계정·공유 그룹과 무관하다 (명세 §3.1). */
    @field:NotNull
    @field:Min(1)
    val headcount: Int,

    /** 원 단위. 생략하면 "예산 정보 없음" 이고 0 과 구분된다. */
    @field:Min(0)
    val budget: Long? = null,

    @field:Size(max = 1000)
    val memo: String? = null,

    /** 생략하면 PRIVATE. 명시적으로 넓히지 않는 한 공개되지 않는다. */
    val visibility: Visibility = Visibility.PRIVATE,

    /** visibility = GROUP 일 때만 의미가 있다. 그 외 값이면 무시된다. */
    val groupIds: List<Long> = emptyList(),
) {
    @get:AssertTrue(message = "종료일은 시작일과 같거나 그보다 뒤여야 합니다.")
    val isValidPeriod: Boolean
        get() = !endDate.isBefore(startDate)

    fun toCommand() = TripCreateCommand(name, startDate, endDate, headcount, budget, memo, visibility, groupIds)
}

/**
 * 여행 기본 정보 수정. **공개 범위는 여기서 바꾸지 않는다** —
 * 같은 값을 두 경로에서 바꾸면 어느 쪽이 최종인지 알기 어려워진다 (명세 §4.3).
 */
data class TripUpdateRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:NotNull
    val startDate: LocalDate,

    @field:NotNull
    val endDate: LocalDate,

    @field:NotNull
    @field:Min(1)
    val headcount: Int,

    @field:Min(0)
    val budget: Long? = null,

    @field:Size(max = 1000)
    val memo: String? = null,
) {
    @get:AssertTrue(message = "종료일은 시작일과 같거나 그보다 뒤여야 합니다.")
    val isValidPeriod: Boolean
        get() = !endDate.isBefore(startDate)

    fun toCommand() = TripUpdateCommand(name, startDate, endDate, headcount, budget, memo)
}

/** 공개 범위만 바꾼다. 공유 그룹 목록은 전체 교체이며, 빠진 그룹의 공유는 해제된다 (명세 §4.3.1). */
data class TripVisibilityUpdateRequest(
    @field:NotNull
    val visibility: Visibility,

    val groupIds: List<Long> = emptyList(),
)

/**
 * 커버 사진 지정·해제.
 *
 * `photoId` 가 null 이면 해제다. 그래서 필수값 검증을 걸지 않는다 — 값이 없는 것과
 * 명시적으로 비우는 것을 구분할 필요가 없다 (명세 §4.3.2).
 */
data class TripCoverUpdateRequest(
    val photoId: Long? = null,
)
