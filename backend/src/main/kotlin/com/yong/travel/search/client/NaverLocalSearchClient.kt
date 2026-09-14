package com.yong.travel.search.client

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.search.dto.NaverLocalSearchItem
import com.yong.travel.search.dto.NaverLocalSearchResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class NaverLocalSearchClient(
    private val naverSearchRestClient: RestClient,
) {
    /** 지역 검색 API 는 호출당 최대 5건(display <= 5)까지만 반환한다. */
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
            throw ApiException(ErrorCode.PLACE_SEARCH_UNAVAILABLE)
        }

        return response?.items ?: emptyList()
    }

    companion object {
        const val MAX_DISPLAY = 5
    }
}
