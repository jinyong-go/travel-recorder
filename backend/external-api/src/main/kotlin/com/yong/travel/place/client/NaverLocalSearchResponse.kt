package com.yong.travel.place.client

/**
 * NAVER API HUB 지역 검색 원본 응답.
 * mapx(경도)·mapy(위도)는 WGS84 좌표에 10^7 을 곱한 정수 문자열이다. 배율은 문서에 없어 실제 응답으로 확인했다.
 */
data class NaverLocalSearchResponse(
    val total: Int = 0,
    val items: List<NaverLocalSearchItem> = emptyList(),
)

/** 지역 검색 결과 한 건. `title` 에는 검색어 강조용 HTML 태그가 섞여 온다. */
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
