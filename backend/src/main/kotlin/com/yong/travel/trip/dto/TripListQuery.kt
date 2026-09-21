package com.yong.travel.trip.dto

/**
 * 여행 목록 조회 범위 (명세 §4.1).
 *
 * 기록 목록의 `RecordScope` 와 값이 같지만 별도 열거형으로 둔다. 두 목록은 판정 대상이 다르고
 * (여행 자신 / 소속 여행), 한쪽이 범위를 늘려도 다른 쪽이 따라가야 할 이유가 없다.
 *
 * 권한을 넓히는 수단이 아니라 이미 볼 수 있는 것 중에서 고르는 수단이다 —
 * 어떤 값을 줘도 서버는 요청자가 볼 권한이 있는 여행만 반환한다.
 */
enum class TripScope {
    /** 내가 소유한 여행 전부 (공개 범위 무관) */
    MINE,

    /** 내가 속한 그룹으로 공유된 타인의 여행 */
    SHARED,

    /** 전체 공개된 여행 (내 것 포함) */
    PUBLIC,
}

/** 여행은 좌표를 갖지 않으므로 거리순이 없다 (명세 §4.3.1). */
enum class TripSort {
    /** 생성 시각 역순 */
    RECENT,

    /** 시작일 역순 */
    START_DATE,
}

data class TripListQuery(
    val scope: TripScope,

    /** 여행 이름 부분 일치 */
    val keyword: String? = null,

    val sort: TripSort = TripSort.RECENT,
)
