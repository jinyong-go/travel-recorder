package com.yong.travel.trip.domain

/**
 * 여행 목록 조회 범위. 볼 수 있는 여행 중에서 고를 뿐 권한을 넓히지 않는다.
 * 기록 목록과 따로 바뀔 수 있어 `RecordScope` 와 나눴다.
 */
enum class TripScope {
    /** 내가 소유한 여행 전부 (공개 범위 무관) */
    MINE,

    /** 내가 속한 그룹으로 공유된 타인의 여행 */
    SHARED,

    /** 전체 공개된 여행 (내 것 포함) */
    PUBLIC,
}

/** 여행 목록 정렬. 여행에는 좌표가 없어 거리순이 없다. */
enum class TripSort {
    /** 생성 시각 역순 */
    RECENT,

    /** 시작일 역순 */
    START_DATE,
}

/** 여행 목록 조회 조건. */
data class TripListQuery(
    val scope: TripScope,

    /** 여행 이름 부분 일치 */
    val keyword: String? = null,

    val sort: TripSort = TripSort.RECENT,
)
