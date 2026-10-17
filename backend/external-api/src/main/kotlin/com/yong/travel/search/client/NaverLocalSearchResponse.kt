package com.yong.travel.search.client

/**
 * NAVER API HUB 지역 검색 원본 응답.
 * mapx(경도)/mapy(위도) 는 WGS84 기준 정수형 문자열(x 10^7)로 내려오므로 변환이 필요하다.
 * 공식 문서는 배율을 밝히지 않으므로 실제 응답 표본으로 한 번 더 확인한다.
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
