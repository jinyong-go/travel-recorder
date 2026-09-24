package com.yong.travel.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
            }
            // 세션 쿠키 인증이므로 CSRF 보호는 유지한다. 토큰 쿠키는 HttpOnly 이고, SPA 는
            // GET /api/auth/session 본문으로 받은 토큰을 X-XSRF-TOKEN 헤더로 돌려보낸다 (명세 §7).
            // 요청 처리기는 기본값(XOR 마스킹)을 쓴다. 토큰이 응답 본문에 실리므로 응답마다
            // 다른 값으로 가려 압축 기반 추측 공격(BREACH)의 단서를 없앤다.
            csrf {
                csrfTokenRepository = csrfTokenRepository()
            }
            authorizeHttpRequests {
                // TODO: 기능 개발 완료 후 authenticated 로 되돌리기
                authorize(anyRequest, permitAll)
            }
            // 네이버 OAuth 는 임시 인메모리 로그인으로 대체된 상태다 (명세 §2.1).
            // 복구할 때 아래 블록과 생성자의 UserService 주입을 함께 되살린다 (명세 §8.1).
            // UserService 와 application.yml 의 네이버 등록 정보는 그대로 두었다 —
            // 이 블록이 없으면 호출되지 않고, 등록 정보가 남아 있어야 Spring Boot 가
            // 기본 인메모리 계정을 자동 생성하지 않는다.
            // oauth2Login {
            //     userInfoEndpoint {
            //         userService = this@SecurityConfig.userService
            //     }
            // }
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
