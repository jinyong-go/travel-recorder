package com.yong.travel.search.service

import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.search.client.NaverLocalSearchClient
import com.yong.travel.search.dto.NaverLocalSearchItem
import com.yong.travel.search.dto.PlaceSearchResultResponse
import org.springframework.stereotype.Service

interface PlaceSearchService {
    fun search(keyword: String, lat: Double?, lng: Double?, page: Int): PageResponse<PlaceSearchResultResponse>
}

@Service
class PlaceSearchServiceImpl(
    private val naverLocalSearchClient: NaverLocalSearchClient,
) : PlaceSearchService {

    override fun search(
        keyword: String,
        lat: Double?,
        lng: Double?,
        page: Int,
    ): PageResponse<PlaceSearchResultResponse> {
        // 원본 API 는 검색어당 최대 5건만 반환한다. 후보 풀을 늘리려면 여기서 검색어 변형
        // (지역명 결합 등)으로 추가 호출한 뒤 아래 중복 제거에 함께 태운다.
        val candidates = naverLocalSearchClient.search(keyword)
            .distinctBy { it.address }
            .map { it.toSearchResult(lat, lng) }

        val sorted = if (lat != null && lng != null) {
            candidates.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            candidates
        }

        val fromIndex = (page.coerceAtLeast(0) * PAGE_SIZE).coerceAtMost(sorted.size)
        val toIndex = (fromIndex + PAGE_SIZE).coerceAtMost(sorted.size)

        return PageResponse(
            content = sorted.subList(fromIndex, toIndex),
            page = page,
            size = PAGE_SIZE,
            totalElements = sorted.size.toLong(),
            totalPages = if (sorted.isEmpty()) 0 else (sorted.size + PAGE_SIZE - 1) / PAGE_SIZE,
        )
    }

    private fun NaverLocalSearchItem.toSearchResult(lat: Double?, lng: Double?): PlaceSearchResultResponse {
        val longitude = (mapx.toDoubleOrNull() ?: 0.0) / COORDINATE_SCALE
        val latitude = (mapy.toDoubleOrNull() ?: 0.0) / COORDINATE_SCALE
        return PlaceSearchResultResponse(
            name = title.replace(HTML_TAG_REGEX, ""),
            category = category,
            address = address,
            roadAddress = roadAddress,
            telephone = telephone,
            latitude = latitude,
            longitude = longitude,
            distanceKm = if (lat != null && lng != null) {
                haversineKm(lat, lng, latitude, longitude).roundTo2Decimals()
            } else {
                null
            },
            link = link,
        )
    }

    companion object {
        /**
         * 한 페이지 5건. 원본 API 가 호출당 5건까지만 주므로 더 키워도 첫 페이지조차 채울 수 없다
         * (명세 §4.1, §4.5). 검색어 변형 집계로 후보 풀이 늘어나면 이 값을 다시 볼 자리다.
         */
        private const val PAGE_SIZE = 5

        private const val COORDINATE_SCALE = 10_000_000.0
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
