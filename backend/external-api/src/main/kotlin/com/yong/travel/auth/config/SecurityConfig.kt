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

/**
 * 보안 필터 체인 구성 — 세션 쿠키 인증, CSRF 쿠키 토큰, 요청 인가.
 *
 * 로그인 여부는 여기서 먼저 막는다. 컨트롤러의 `requireLogin` 은 사용자 id 를 얻는 경로이자 두 번째 관문이다.
 */
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
            // 세션 쿠키 인증이므로 CSRF 보호는 유지한다. 토큰 쿠키는 HttpOnly 이고, SPA 는
            // GET /api/auth/session 본문으로 받은 토큰을 X-XSRF-TOKEN 헤더로 돌려보낸다.
            // 요청 처리기는 기본값(XOR 마스킹)을 쓴다. 토큰이 응답 본문에 실리므로 응답마다
            // 다른 값으로 가려 압축 기반 추측 공격(BREACH)의 단서를 없앤다.
            csrf {
                csrfTokenRepository = csrfTokenRepository()
            }
            // 사전 요청(preflight)은 쿠키 없이 오므로 MVC 의 CORS 처리에 닿기 전에 401 이 된다.
            // 필터가 WebConfig 의 CORS 규칙으로 먼저 처리하게 한다.
            cors { }
            authorizeHttpRequests {
                // 비로그인에게 열린 경로만 나열하고 나머지는 모두 로그인을 요구한다. 기본값이 열림이면
                // requireLogin 하나를 빠뜨린 경로가 곧 노출이므로, 여는 것은 여기 한 줄을 더하는 결정이어야 한다.
                // 열린 경로의 범위 판정(전체 공개만 보이는 것)은 서비스의 조회 조건이 한다.
                authorize(HttpMethod.GET, "/api/trips/**", permitAll)
                authorize(HttpMethod.GET, "/api/records/**", permitAll)
                authorize(HttpMethod.GET, "/api/tags", permitAll)
                authorize(HttpMethod.GET, "/api/files/**", permitAll)
                authorize(HttpMethod.GET, "/api/auth/session", permitAll)
                authorize(HttpMethod.POST, "/api/auth/login", permitAll)
                // 오류 응답을 그리는 내부 경로. 막으면 비로그인 요청의 오류가 모두 401 로 바뀐다.
                authorize("/error", permitAll)
                // 이 체인은 관리 포트에도 걸린다. 수집기(Prometheus)와 상태 확인은 로그인하지 않으므로 노출한 두
                // 엔드포인트만 연다. 외부 차단은 관리 포트를 공개하지 않는 배포가 맡는다.
                authorize(EndpointRequest.to("health", "prometheus"), permitAll)
                authorize(anyRequest, authenticated)
            }
            exceptionHandling {
                // 필터에서 막힌 요청도 컨트롤러가 던진 UNAUTHENTICATED 와 같은 본문으로 답한다.
                // 프론트엔드가 어느 단계에서 막혔든 code 로 분기할 수 있어야 한다.
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
            // 네이버 OAuth 는 임시 인메모리 로그인으로 대체된 상태다. 복구할 때는 생성자에 UserService 를
            // 주입받고, 이 자리에 oauth2Login 블록을 두어 userInfoEndpoint 의 userService 로 그것을 건다.
            // 로그인 시작·콜백 경로(/oauth2/**, /login/oauth2/**)도 위 목록에 열어야 한다.
            // UserService 와 application.yml 의 네이버 등록 정보는 그대로 두었다 — 그 블록이 없으면
            // 호출되지 않고, 등록 정보가 남아 있어야 Spring Boot 가 기본 인메모리 계정을 자동 생성하지 않는다.
        }
        return http.build()
    }

    /**
     * CSRF 토큰 저장소. 로그인·로그아웃 컨트롤러가 토큰을 비울 때 같은 쿠키 설정을 써야 하므로
     * 빈으로 꺼내 둔다 — 따로 만들면 쿠키 이름·경로가 어긋나 비우지 못할 수 있다.
     */
    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository = CookieCsrfTokenRepository()
}
