package com.yong.travel.search.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** NAVER API HUB(지역 검색) 인증 정보. 네이버 로그인/NCP Maps 와는 별도 키다. */
@Component
@ConfigurationProperties(prefix = "naver.api")
class NaverSearchProperties {
    var clientId: String = ""
    var clientSecret: String = ""
    var baseUrl: String = "https://naverapihub.apigw.ntruss.com/search/v1/local"
}
