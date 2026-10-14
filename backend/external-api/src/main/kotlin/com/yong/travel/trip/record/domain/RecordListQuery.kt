package com.yong.travel.trip.record.domain

/**
 * 목록 조회 범위. 세 범위는 성격이 달라 한 목록에 섞지 않는다.
 *
 * 권한을 넓히는 수단이 아니라 이미 볼 수 있는 것 중에서 고르는 수단이다 —
 * 어떤 값을 줘도 서버는 요청자가 볼 권한이 있는 기록만 반환한다.
 *
 * 판정 근거는 기록이 아니라 소속 여행에 있다.
 */
enum class RecordScope {
    /** 내 여행의 기록 전부 (공개 범위 무관) */
    MINE,

    /** 내가 속한 그룹으로 공유된 타인 여행의 기록 */
    SHARED,

    /** 전체 공개된 여행의 기록 (내 것 포함) */
    PUBLIC,
}

/** 기록 목록 정렬. 거리순은 기준 좌표가 있을 때만 의미가 있다. */
enum class RecordSort {
    RECENT,

    /** 등록순. 여행 상세의 하위 기록이 쓴다. */
    OLDEST,
    RATING,
    DISTANCE,
}

/** 기록 목록 조회 조건. 컨트롤러가 쿼리 파라미터를 옮겨 담아 서비스에 넘긴다. */
data class RecordListQuery(
    /** null 이면 `tripId` 가 반드시 있고, 그 여행의 하위 기록 전부다. */
    val scope: RecordScope?,

    /** 특정 여행의 하위 기록으로 한정한다. 여행 상세 화면이 이 경우다. */
    val tripId: Long? = null,

    val category: Category? = null,
    val tag: String? = null,
    val keyword: String? = null,
    val sort: RecordSort = RecordSort.RECENT,

    /** 기준 위치. 거리 계산에 쓰며, `sort = DISTANCE` 면 반드시 있다 (컨트롤러가 보장). */
    val origin: GeoPoint? = null,
)
