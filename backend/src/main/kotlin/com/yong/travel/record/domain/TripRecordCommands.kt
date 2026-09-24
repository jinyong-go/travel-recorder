package com.yong.travel.record.domain

/**
 * 기록 등록 입력. 형식 검증(평점 0.5 단위 등)은 요청 DTO 에서 끝났고, 컨트롤러가 옮겨 담아 넘긴다.
 *
 * 요청 DTO 와 나눠 두는 이유는 `TripCreateCommand` 와 같다 — 서비스가 HTTP 요청 모양을 모르게 한다.
 */
data class TripRecordCreateCommand(
    /** 소속 여행. 요청자가 소유한 여행이어야 한다 (명세 §4.4). */
    val tripId: Long,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
)

/** 기록 수정 입력. 소속 여행은 담지 않는다 — 여행 이동은 `TripRecordService.changeTrip` 의 몫이다. */
data class TripRecordUpdateCommand(
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
)
