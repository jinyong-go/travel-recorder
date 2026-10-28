package com.yong.travel.place.service

import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.util.trimToNull
import com.yong.travel.place.client.NaverLocalSearchClient
import com.yong.travel.place.client.NaverLocalSearchItem
import com.yong.travel.place.domain.PlaceCandidate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils

/** 네이버 지역 검색으로 장소 후보를 찾는다. */
@Service
class PlaceSearchService(
    private val naverLocalSearchClient: NaverLocalSearchClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검색어로 장소 후보를 찾는다. 순서는 원본 API 결과를 따르고, 거리순 정렬은 프론트엔드가 한다.
     *
     * @param page 0부터 시작
     * @throws ApiException `PLACE_SEARCH_UNAVAILABLE` — 외부 API 호출 실패
     */
    fun search(keyword: String, page: Int): PageResult<PlaceCandidate> {
        // 원본 API 는 검색어당 5건까지만 준다.
        val candidates = naverLocalSearchClient.search(keyword)
            .mapNotNull { it.toCandidate() }
            // 한 건물에 장소가 여럿일 수 있어 주소와 이름을 함께 비교한다.
            .distinctBy { it.name to it.address }

        log.debug("장소 검색 page={} count={}", page, candidates.size)

        // 원본 API 에는 페이지 개념이 없어 직접 자른다.
        val current = page.coerceAtLeast(0)
        val fromIndex = (current * PAGE_SIZE).coerceAtMost(candidates.size)
        val toIndex = (fromIndex + PAGE_SIZE).coerceAtMost(candidates.size)
        return PageResult(candidates.subList(fromIndex, toIndex), current, PAGE_SIZE, candidates.size.toLong())
    }

    /**
     * 원본 응답 항목을 후보로 바꾼다. 좌표가 없으면 엉뚱한 위치가 저장되지 않도록 null 을 돌려준다.
     * 원본은 빈 값을 빈 문자열로 보내므로 선택 항목은 null 로 바꾼다.
     */
    private fun NaverLocalSearchItem.toCandidate(): PlaceCandidate? {
        val latitude = mapy.toDoubleOrNull() ?: return null
        val longitude = mapx.toDoubleOrNull() ?: return null
        return PlaceCandidate(
            name = HtmlUtils.htmlUnescape(title.replace(HTML_TAG_REGEX, "")),
            category = category.trimToNull(),
            address = address,
            roadAddress = roadAddress.trimToNull(),
            telephone = telephone.trimToNull(),
            latitude = latitude / COORDINATE_SCALE,
            longitude = longitude / COORDINATE_SCALE,
            link = link.trimToNull(),
        )
    }

    companion object {
        /** 원본 API 가 호출당 5건까지만 주므로 그 이상은 의미가 없다. */
        private const val PAGE_SIZE = 5

        private const val COORDINATE_SCALE = 10_000_000.0
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
