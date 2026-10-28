package com.yong.travel.place.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/** NAVER API HUB 지역 검색을 부를 `RestClient`. NCP API Gateway 인증 헤더와 Accept 를 기본값으로 싣는다. */
@Configuration
class NaverSearchClientConfig {

    /**
     * 지역 검색 API 클라이언트를 만든다.
     *
     * 관측 레지스트리를 걸어야 호출마다 http.client.requests 지표가 남는다. 빠뜨리면 외부 호출이
     * 지표에서 통째로 빠진다. 관측이 걸린 빌더를 내주는 Spring Boot 모듈(spring-boot-restclient)을
     * 들이지 않고 레지스트리만 직접 건다 — 쓰는 클라이언트가 이것 하나뿐이다.
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
            // Accept 를 명시하지 않으면 API 가 JSON 본문에 text/plain 을 붙여 보내 변환에 실패한다.
            .defaultHeader(HttpHeaders.ACCEPT, JSON_UTF8)
            .observationRegistry(observationRegistry)
            .build()

    private companion object {
        const val JSON_UTF8 = "application/json;charset=UTF-8"
    }
}
