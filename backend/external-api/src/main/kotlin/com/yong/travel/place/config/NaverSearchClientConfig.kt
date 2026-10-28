package com.yong.travel.place.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/** 지역 검색 API 클라이언트 설정. */
@Configuration
class NaverSearchClientConfig {

    /**
     * 인증 헤더를 실은 지역 검색 API 클라이언트를 만든다.
     * 외부 호출 지표가 남도록 관측 레지스트리를 직접 건다. 클라이언트가 하나뿐이라 별도 모듈은 들이지 않았다.
     */
    @Bean
    fun naverSearchRestClient(
        properties: NaverSearchProperties,
        observationRegistry: ObservationRegistry,
    ): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.clientId)
            .defaultHeader("X-NCP-APIGW-API-KEY", properties.clientSecret)
            // Accept 가 없으면 API 가 JSON 을 text/plain 으로 보내 변환에 실패한다.
            .defaultHeader(HttpHeaders.ACCEPT, JSON_UTF8)
            .observationRegistry(observationRegistry)
            .build()

    private companion object {
        const val JSON_UTF8 = "application/json;charset=UTF-8"
    }
}
