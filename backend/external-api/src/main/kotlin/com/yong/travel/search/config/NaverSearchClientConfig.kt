package com.yong.travel.search.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/** NAVER API HUB 지역 검색을 부를 `RestClient`. NCP API Gateway 인증 헤더를 기본값으로 싣는다. */
@Configuration
class NaverSearchClientConfig {

    /**
     * 지역 검색 API 클라이언트를 만든다.
     *
     * @param properties 기본 주소와 클라이언트 id·secret
     * @return 인증 헤더를 기본값으로 실은 클라이언트
     */
    @Bean
    fun naverSearchRestClient(properties: NaverSearchProperties): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.clientId)
            .defaultHeader("X-NCP-APIGW-API-KEY", properties.clientSecret)
            .build()
}
