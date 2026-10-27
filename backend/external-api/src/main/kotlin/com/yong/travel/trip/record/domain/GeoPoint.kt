package com.yong.travel.trip.record.domain

/**
 * 요청자의 기준 위치. 거리 계산과 거리순 정렬에만 쓰고 저장하지 않는다.
 *
 * 위도·경도를 따로 nullable 로 들고 다니면 한쪽만 있는 상태가 생긴다. 그런 상태는 쓸 수 없으므로
 * 둘을 한 값으로 묶어, 기준 위치는 "있다/없다" 둘 중 하나만 되게 한다.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        /** WGS84 위경도 범위 안인지. 기록의 좌표와 목록의 기준 위치가 같은 규칙을 쓰도록 여기 한 곳에 둔다. */
        fun isValid(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
