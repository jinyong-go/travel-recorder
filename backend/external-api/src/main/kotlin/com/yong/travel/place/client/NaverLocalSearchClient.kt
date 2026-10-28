package com.yong.travel.place.client

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** NAVER API HUB 지역 검색을 호출한다. */
@Component
class NaverLocalSearchClient(
    private val naverSearchRestClient: RestClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 지역 검색 결과를 가져온다. 호출당 최대 5건이다.
     *
     * @param display 1~5 로 잘라 보낸다
     * @return 응답 본문이 없으면 빈 목록
     * @throws ApiException `PLACE_SEARCH_UNAVAILABLE` — 호출 실패
     */
    fun search(query: String, display: Int = MAX_DISPLAY): List<NaverLocalSearchItem> {
        val response = try {
            naverSearchRestClient.get()
                // 검색어가 호출 지표의 경로 라벨에 남지 않도록 템플릿이 아닌 빌더로 붙인다.
                .uri { builder ->
                    builder
                        .queryParam("query", query)
                        .queryParam("display", display.coerceIn(1, MAX_DISPLAY))
                        .build()
                }
                .retrieve()
                .body(NaverLocalSearchResponse::class.java)
        } catch (e: RestClientException) {
            // 사용자에게는 고정 문구만 나가므로 원인은 로그로 남긴다.
            log.warn("장소 검색 API 호출 실패", e)
            throw ApiException(ErrorCode.PLACE_SEARCH_UNAVAILABLE)
        }

        return response?.items ?: emptyList()
    }

    companion object {
        const val MAX_DISPLAY = 5
    }
}
