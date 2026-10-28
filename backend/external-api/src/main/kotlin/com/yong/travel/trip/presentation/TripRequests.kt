package com.yong.travel.trip.presentation

import com.yong.travel.trip.domain.TripCreateCommand
import com.yong.travel.trip.domain.TripUpdateCommand
import com.yong.travel.trip.domain.TripVisibility
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/** 여행 생성 요청. */
data class TripCreateRequest(
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

    val visibility: TripVisibility = TripVisibility.PRIVATE,

    /** GROUP 일 때만 쓴다. */
    val groupIds: List<Long> = emptyList(),
) {
    @get:AssertTrue(message = "종료일은 시작일과 같거나 그보다 뒤여야 합니다.")
    val isValidPeriod: Boolean
        get() = !endDate.isBefore(startDate)

    /** 요청 → 서비스 입력. */
    fun toCommand() = TripCreateCommand(name, startDate, endDate, headcount, budget, memo, visibility, groupIds)
}

/** 여행 기본 정보 수정 요청. 공개 범위는 따로 바꾼다. */
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

    /** 요청 → 서비스 입력. */
    fun toCommand() = TripUpdateCommand(name, startDate, endDate, headcount, budget, memo)
}

/** 공개 범위 변경 요청. 공유 그룹은 통째로 바뀐다. */
data class TripVisibilityUpdateRequest(
    @field:NotNull
    val visibility: TripVisibility,

    val groupIds: List<Long> = emptyList(),
)

/** 커버 사진 지정 요청. `photoId` 가 null 이면 해제한다. */
data class TripCoverUpdateRequest(
    val photoId: UUID? = null,
)
