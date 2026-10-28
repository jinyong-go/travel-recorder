package com.yong.travel.trip.record.domain

/** 기록 목록 조회 범위. 소속 여행으로 판정한다. */
enum class RecordScope {
    /** 내 여행의 기록 전부 (공개 범위 무관) */
    MINE,

    /** 내가 속한 그룹으로 공유된 타인 여행의 기록 */
    SHARED,

    /** 전체 공개된 여행의 기록 (내 것 포함) */
    PUBLIC,
}

/** 기록 목록 정렬. 거리순은 기준 위치가 있어야 한다. */
enum class RecordSort {
    RECENT,

    /** 등록순 */
    OLDEST,
    RATING,
    DISTANCE,
}

/** 기록 목록 조회 조건. */
data class RecordListQuery(
    /** null 이면 `tripId` 여행의 기록 전부다. */
    val scope: RecordScope?,

    /** 이 여행의 기록으로 좁힌다. */
    val tripId: Long? = null,

    val category: Category? = null,
    val tag: String? = null,
    val keyword: String? = null,
    val sort: RecordSort = RecordSort.RECENT,

    /** 기준 위치. 거리순이면 반드시 있다. */
    val origin: GeoPoint? = null,
)
