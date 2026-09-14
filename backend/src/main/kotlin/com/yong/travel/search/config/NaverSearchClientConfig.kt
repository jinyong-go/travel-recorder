package com.yong.travel.search.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class NaverSearchClientConfig {

    @Bean
    fun naverSearchRestClient(properties: NaverSearchProperties): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("X-Naver-Client-Id", properties.clientId)
            .defaultHeader("X-Naver-Client-Secret", properties.clientSecret)
            .build()
}
