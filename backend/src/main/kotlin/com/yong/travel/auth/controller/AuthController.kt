package com.yong.travel.auth.controller

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.auth.service.AuthService
import com.yong.travel.common.web.requireLogin
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    /** 로그인 시작은 Spring Security 가 제공하는 GET /oauth2/authorization/naver 를 사용한다. */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: CustomOAuth2User?): UserResponse =
        authService.getCurrentUser(requireLogin(principal))

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
