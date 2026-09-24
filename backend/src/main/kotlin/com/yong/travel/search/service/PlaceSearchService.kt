package com.yong.travel.search.service

import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.search.client.NaverLocalSearchClient
import com.yong.travel.search.client.NaverLocalSearchItem
import com.yong.travel.search.domain.PlaceCandidate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val naverLocalSearchClient: NaverLocalSearchClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun search(
        keyword: String,
        lat: Double?,
        lng: Double?,
        page: Int,
    ): Page<PlaceCandidate> {
        // 원본 API 는 검색어당 최대 5건만 반환한다. 후보 풀을 늘리려면 여기서 검색어 변형
        // (지역명 결합 등)으로 추가 호출한 뒤 아래 중복 제거에 함께 태운다.
        val candidates = naverLocalSearchClient.search(keyword)
            .distinctBy { it.address }
            .map { it.toCandidate(lat, lng) }

        val sorted = if (lat != null && lng != null) {
            candidates.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            candidates
        }

        // 기준 좌표(lat,lng)는 남기지 않는다. 계산에만 쓰고 사용자와 묶지 않기로 한 값이다 (명세 §7).
        log.debug("장소 검색 page={} 거리정렬={} 후보={}건", page, lat != null && lng != null, sorted.size)

        // 원본 API 가 페이지를 모르므로 모아 온 후보를 여기서 자른다.
        val pageable = PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE)
        val fromIndex = pageable.offset.toInt().coerceAtMost(sorted.size)
        val toIndex = (fromIndex + PAGE_SIZE).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(fromIndex, toIndex), pageable, sorted.size.toLong())
    }

    /** 원본 응답 → 후보. 좌표 환산과 HTML 태그 제거가 여기서 끝난다 (명세 §1.3). */
    private fun NaverLocalSearchItem.toCandidate(lat: Double?, lng: Double?): PlaceCandidate {
        val longitude = (mapx.toDoubleOrNull() ?: 0.0) / COORDINATE_SCALE
        val latitude = (mapy.toDoubleOrNull() ?: 0.0) / COORDINATE_SCALE
        return PlaceCandidate(
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
