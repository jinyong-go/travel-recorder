package com.yong.travel.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** MVC 설정 — CORS. */
@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

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
