package com.yong.travel.trip.record.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

/**
 * 두 위경도 사이의 대권 거리를 구한다. 지구를 반지름 6371km 의 구로 본 하버사인 공식이다.
 *
 * @param lat1 첫 지점의 위도
 * @param lng1 첫 지점의 경도
 * @param lat2 둘째 지점의 위도
 * @param lng2 둘째 지점의 경도
 * @return 두 지점 사이 거리(km)
 */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * 소수 둘째 자리까지 반올림한다. 응답의 거리(km) 표기에 쓴다.
 *
 * @return 반올림한 값
 */
fun Double.roundTo2Decimals(): Double = (this * 100).roundToLong() / 100.0
