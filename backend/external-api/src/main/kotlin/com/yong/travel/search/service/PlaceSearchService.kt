package com.yong.travel.search.service

import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.util.trimToNull
import com.yong.travel.search.client.NaverLocalSearchClient
import com.yong.travel.search.client.NaverLocalSearchItem
import com.yong.travel.search.domain.PlaceCandidate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils

/** 네이버 지역 검색 결과를 후보로 바꿔 페이지 자르기까지 해서 돌려준다. */
@Service
class PlaceSearchService(
    private val naverLocalSearchClient: NaverLocalSearchClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검색어로 장소 후보를 찾는다. 순서는 원본 API 의 순서를 그대로 따른다.
     *
     * 거리순 정렬은 하지 않는다. 기준 위치를 서버로 받지 않고 프론트엔드가 받은 후보로 직접 계산한다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @throws ApiException `PLACE_SEARCH_UNAVAILABLE` — 외부 검색 API 호출이 실패했을 때
     */
    fun search(keyword: String, page: Int): PageResult<PlaceCandidate> {
        // 원본 API 는 검색어당 최대 5건만 반환한다. 후보 풀을 늘리려면 여기서 검색어 변형
        // (지역명 결합 등)으로 추가 호출한 뒤 아래 중복 제거에 함께 태운다.
        val candidates = naverLocalSearchClient.search(keyword)
            .mapNotNull { it.toCandidate() }
            // 같은 건물에 여러 장소가 있으므로 주소만으로는 같은 장소가 아니다. 원본 이름은 검색어에 따라
            // 강조 태그가 달라지므로, 태그·엔티티를 정리한 뒤의 이름으로 비교한다.
            .distinctBy { it.name to it.address }

        log.debug("장소 검색 page={} count={}", page, candidates.size)

        // 원본 API 가 페이지를 모르므로 모아 온 후보를 여기서 자른다.
        val current = page.coerceAtLeast(0)
        val fromIndex = (current * PAGE_SIZE).coerceAtMost(candidates.size)
        val toIndex = (fromIndex + PAGE_SIZE).coerceAtMost(candidates.size)
        return PageResult(candidates.subList(fromIndex, toIndex), current, PAGE_SIZE, candidates.size.toLong())
    }

    /**
     * 원본 응답 → 후보. 좌표 환산, 강조 태그 제거, HTML 엔티티 복원이 여기서 끝난다.
     *
     * 좌표를 읽을 수 없는 항목은 버린다. (0, 0) 으로 채우면 엉뚱한 위치가 기록에 그대로 저장된다.
     * 원본은 값이 없을 때 빈 문자열을 보내므로 선택 항목은 null 로 바꾼다 — 빈 링크가 기록에 남지 않게 한다.
     *
     * @return 후보. 좌표를 읽을 수 없으면 null
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
        /**
         * 한 페이지 5건. 원본 API 가 호출당 5건까지만 주므로 더 키워도 첫 페이지조차 채울 수 없다.
         * 검색어 변형 집계로 후보 풀이 늘어나면 이 값을 다시 볼 자리다.
         */
        private const val PAGE_SIZE = 5

        private const val COORDINATE_SCALE = 10_000_000.0
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
