package com.yong.travel.place.domain

/**
 * 장소 검색 후보 한 건. 저장되지 않고 기록 등록 폼을 채우는 데만 쓴다.
 * 좌표는 도(度) 단위이고 이름에는 HTML 태그가 없다.
 */
data class PlaceCandidate(
    val name: String,
    val category: String?,
    val address: String,
    val roadAddress: String?,
    val telephone: String?,
    val latitude: Double,
    val longitude: Double,
    val link: String?,
)
