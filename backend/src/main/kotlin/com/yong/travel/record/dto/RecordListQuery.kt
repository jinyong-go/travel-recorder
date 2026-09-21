package com.yong.travel.record.dto

import com.yong.travel.record.domain.Category

/**
 * 목록 조회 범위. 세 범위는 성격이 달라 한 목록에 섞지 않는다.
 *
 * 권한을 넓히는 수단이 아니라 이미 볼 수 있는 것 중에서 고르는 수단이다 —
 * 어떤 값을 줘도 서버는 요청자가 볼 권한이 있는 기록만 반환한다.
 *
 * 판정 근거는 기록이 아니라 소속 여행에 있다 (명세 §4.4.1).
 */
enum class RecordScope {
    /** 내 여행의 기록 전부 (공개 범위 무관) */
    MINE,

    /** 내가 속한 그룹으로 공유된 타인 여행의 기록 */
    SHARED,

    /** 전체 공개된 여행의 기록 (내 것 포함) */
    PUBLIC,
}

enum class RecordSort {
    RECENT,
    RATING,
    DISTANCE,
}

data class RecordListQuery(
    val scope: RecordScope,

    /** 특정 여행의 하위 기록으로 한정한다. 여행 상세 화면이 이 경우다 (명세 §4.4.1). */
    val tripId: Long? = null,

    val category: Category? = null,
    val tag: String? = null,
    val keyword: String? = null,
    val sort: RecordSort = RecordSort.RECENT,
    val lat: Double? = null,
    val lng: Double? = null,
)
