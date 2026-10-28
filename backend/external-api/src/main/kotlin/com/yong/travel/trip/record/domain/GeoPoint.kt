package com.yong.travel.trip.record.domain

/** 요청자의 기준 위치. 거리 계산에만 쓰고 저장하지 않는다. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        /** 위경도가 유효한 범위인지 확인한다. */
        fun isValid(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
