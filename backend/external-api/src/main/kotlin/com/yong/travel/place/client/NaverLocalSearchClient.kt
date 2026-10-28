package com.yong.travel.place.client

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** NAVER API HUB 지역 검색 호출. 응답 모델은 [NaverLocalSearchResponse] 다. */
@Component
class NaverLocalSearchClient(
    private val naverSearchRestClient: RestClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 지역 검색 API 를 부른다. 호출당 최대 5건(display <= 5)까지만 반환된다.
     *
     * @param display 받을 건수. 1~5 로 잘라 보낸다
     * @return 원본 검색 결과. 응답 본문이 없으면 빈 목록
     * @throws ApiException `PLACE_SEARCH_UNAVAILABLE` — 호출이 실패했을 때
     */
    fun search(query: String, display: Int = MAX_DISPLAY): List<NaverLocalSearchItem> {
        val response = try {
            naverSearchRestClient.get()
                // 검색어를 URI 템플릿 문자열에 넣지 않고 빌더로 붙인다. 호출 지표의 경로 라벨은 템플릿이라
                // 이렇게 해야 사용자가 입력한 검색어가 지표에 남지 않는다.
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
