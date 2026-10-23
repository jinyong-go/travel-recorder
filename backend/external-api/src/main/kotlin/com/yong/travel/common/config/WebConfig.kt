package com.yong.travel.common.config

import com.yong.travel.common.web.EnumParamConverterFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** MVC 설정 — 쿼리 파라미터 열거형 변환기와 CORS. */
@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    /** `scope=mine` 처럼 명세가 규정한 소문자 열거형 값을 받기 위한 변환기를 등록한다. */
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverterFactory(EnumParamConverterFactory)
    }

    /**
     * 프론트엔드 출처에서 쿠키를 실어 `/api` 하위 경로를 부를 수 있게 한다.
     *
     * @param registry CORS 규칙 등록부. 허용 출처는 `app.cors.allowed-origins` 설정값이다
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowCredentials(true)
    }
}
