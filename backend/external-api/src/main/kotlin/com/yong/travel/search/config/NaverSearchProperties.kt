package com.yong.travel.search.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** 네이버 검색 오픈API(지역) 인증 정보. 네이버 로그인/NCP Maps 와는 별도 키다. */
@Component
@ConfigurationProperties(prefix = "naver.search")
class NaverSearchProperties {
    var clientId: String = ""
    var clientSecret: String = ""
    var baseUrl: String = "https://openapi.naver.com/v1/search/local.json"
}
