package com.yong.travel.search.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/** 네이버 검색 오픈API 를 부를 `RestClient`. 인증 헤더를 기본값으로 싣는다. */
@Configuration
class NaverSearchClientConfig {

    /**
     * 네이버 검색 API 클라이언트를 만든다.
     *
     * @param properties 기본 주소와 클라이언트 id·secret
     * @return 인증 헤더를 기본값으로 실은 클라이언트
     */
    @Bean
    fun naverSearchRestClient(properties: NaverSearchProperties): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("X-Naver-Client-Id", properties.clientId)
            .defaultHeader("X-Naver-Client-Secret", properties.clientSecret)
            .build()
}
