package com.yong.travel.auth.config

import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.error.ErrorResponse
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import tools.jackson.databind.json.JsonMapper

/** 세션 인증, CSRF, 요청 인가를 설정한다. */
@Configuration
class SecurityConfig(
    private val jsonMapper: JsonMapper,
) {

    /** 세션·CSRF·CORS·인가 규칙을 건 필터 체인을 만든다. */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
            }
            // 세션 쿠키로 인증하므로 CSRF 보호를 켠다. 토큰이 응답 본문에 실리므로
            // BREACH 공격을 막는 기본 마스킹을 그대로 쓴다.
            csrf {
                csrfTokenRepository = csrfTokenRepository()
            }
            // preflight 는 쿠키 없이 오므로 인가보다 먼저 CORS 를 처리한다.
            cors { }
            authorizeHttpRequests {
                // 비로그인에게 여는 경로만 나열하고 나머지는 닫는다. 기본값을 닫아 두어야
                // 로그인 확인을 빠뜨린 경로가 노출되지 않는다.
                authorize(HttpMethod.GET, "/api/trips/**", permitAll)
                authorize(HttpMethod.GET, "/api/records/**", permitAll)
                authorize(HttpMethod.GET, "/api/tags", permitAll)
                authorize(HttpMethod.GET, "/api/files/**", permitAll)
                authorize(HttpMethod.GET, "/api/auth/session", permitAll)
                authorize(HttpMethod.POST, "/api/auth/login", permitAll)
                // 막으면 비로그인 요청의 오류가 모두 401 로 바뀐다.
                authorize("/error", permitAll)
                // 지표 수집과 상태 확인은 로그인하지 않으므로 연다. 관리 포트는 외부에 공개하지 않는다.
                authorize(EndpointRequest.to("health", "prometheus"), permitAll)
                authorize(anyRequest, authenticated)
            }
            exceptionHandling {
                // 필터에서 막혀도 다른 오류와 같은 본문으로 답해 프론트엔드가 code 로 분기하게 한다.
                authenticationEntryPoint = AuthenticationEntryPoint { _, response, _ ->
                    val code = ErrorCode.UNAUTHENTICATED
                    response.status = code.status.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = Charsets.UTF_8.name()
                    jsonMapper.writeValue(
                        response.outputStream,
                        ErrorResponse(code.name, code.defaultMessage, code.status.value()),
                    )
                }
            }
            // 네이버 OAuth 는 임시 로그인으로 대체된 상태다. 복구하면 이 자리에 oauth2Login 을 둔다.
            // 네이버 등록 정보는 남겨 둔다. 지우면 Spring Boot 가 기본 계정을 자동 생성한다.
        }
        return http.build()
    }

    /** CSRF 토큰 저장소. 로그인·로그아웃에서 같은 쿠키 설정으로 토큰을 비우도록 빈으로 둔다. */
    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository = CookieCsrfTokenRepository()
}
