package com.yong.travel.auth.config

import com.yong.travel.auth.service.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(
    private val userService: UserService,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
            }
            authorizeHttpRequests {
                // TODO: 기능 개발 완료 후 authenticated 로 되돌리기
                authorize(anyRequest, permitAll)
            }
            oauth2Login {
                userInfoEndpoint {
                    userService = this@SecurityConfig.userService
                }
            }
        }
        return http.build()
    }
}
