package com.yong.travel.place.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** 지역 검색 API 인증 정보. 네이버 로그인·지도와는 다른 키다. */
@Component
@ConfigurationProperties(prefix = "naver.api")
class NaverSearchProperties {
    var clientId: String = ""
    var clientSecret: String = ""
    var baseUrl: String = "https://naverapihub.apigw.ntruss.com/search/v1/local"
}
