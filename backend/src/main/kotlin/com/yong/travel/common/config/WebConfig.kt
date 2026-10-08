package com.yong.travel.common.config

import com.yong.travel.common.web.EnumParamConverterFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    /** `scope=mine` 처럼 명세가 규정한 소문자 열거형 값을 받기 위한 변환기 (§4.1). */
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverterFactory(EnumParamConverterFactory)
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowCredentials(true)
    }
}
