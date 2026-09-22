package com.yong.travel.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
            }
            // 세션 쿠키 인증이므로 CSRF 보호는 유지하되, SPA 가 XSRF-TOKEN 쿠키를 읽어
            // X-XSRF-TOKEN 헤더로 돌려보낼 수 있게 쿠키 저장소를 쓴다.
            // csrfRequestAttributeName = null 은 지연 로딩을 끄는 설정으로, 이게 없으면
            // 토큰을 실제로 읽는 요청이 없어 쿠키가 발급되지 않는다.
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler().apply {
                    setCsrfRequestAttributeName(null)
                }
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
}
