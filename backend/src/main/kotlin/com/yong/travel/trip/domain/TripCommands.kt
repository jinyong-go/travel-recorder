package com.yong.travel.trip.domain

import java.time.LocalDate

/**
 * 여행 생성 입력. 형식 검증은 요청 DTO 에서 끝났고, 컨트롤러가 옮겨 담아 서비스에 넘긴다.
 *
 * 요청 DTO 와 모양이 같지만 계층이 다르다. 서비스가 HTTP 요청 모양을 알면 화면 입력이 바뀔 때마다
 * 서비스가 끌려 들어온다 — 도메인 객체와 응답 DTO 를 나눈 것과 같은 이유다.
 */
data class TripCreateCommand(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
    val visibility: Visibility,

    /** visibility = GROUP 일 때만 의미가 있다. 그 외 값이면 무시된다. */
    val groupIds: List<Long>,
)

/** 여행 기본 정보 수정 입력. 공개 범위는 담지 않는다 — `TripService.changeVisibility` 의 몫이다 (명세 §4.3). */
data class TripUpdateCommand(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
)
