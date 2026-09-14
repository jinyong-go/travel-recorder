package com.yong.travel.search.dto

/**
 * 네이버 지역 검색 오픈API 원본 응답.
 * mapx/mapy 는 정수형 문자열(위경도 x 10^7)로 내려오므로 변환이 필요하다.
 * 실제 연동 시 표본 응답으로 타입/좌표계를 한 번 더 확인한다.
 */
data class NaverLocalSearchResponse(
    val total: Int = 0,
    val items: List<NaverLocalSearchItem> = emptyList(),
)

data class NaverLocalSearchItem(
    val title: String,
    val link: String? = null,
    val category: String? = null,
    val description: String? = null,
    val telephone: String? = null,
    val address: String,
    val roadAddress: String? = null,
    val mapx: String,
    val mapy: String,
)
