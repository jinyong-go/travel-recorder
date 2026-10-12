package com.yong.travel.search.client

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** 네이버 지역 검색 오픈API 호출. 응답 모델은 [NaverLocalSearchResponse] 다. */
@Component
class NaverLocalSearchClient(
    private val naverSearchRestClient: RestClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 지역 검색 API 를 부른다. 호출당 최대 5건(display <= 5)까지만 반환된다.
     *
     * @param query 검색어
     * @param display 받을 건수. 1~5 로 잘라 보낸다
     * @return 원본 검색 결과. 응답 본문이 없으면 빈 목록
     * @throws ApiException `PLACE_SEARCH_UNAVAILABLE` — 호출이 실패했을 때
     */
    fun search(query: String, display: Int = MAX_DISPLAY): List<NaverLocalSearchItem> {
        val response = try {
            naverSearchRestClient.get()
                .uri { builder ->
                    builder
                        .queryParam("query", query)
                        .queryParam("display", display.coerceIn(1, MAX_DISPLAY))
                        .build()
                }
                .retrieve()
                .body(NaverLocalSearchResponse::class.java)
        } catch (e: RestClientException) {
            // 사용자에게는 고정 문구만 나가므로 원인은 이 로그에만 남는다.
            log.warn("장소 검색 API 호출 실패", e)
            throw ApiException(ErrorCode.PLACE_SEARCH_UNAVAILABLE)
        }

        return response?.items ?: emptyList()
    }

    companion object {
        const val MAX_DISPLAY = 5
    }
}
