package com.yong.travel.auth.controller

import com.yong.travel.auth.dto.MeResponse
import com.yong.travel.auth.security.LoginUser
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

    /**
     * 로그인한 본인 정보 조회. 이메일이 담기는 유일한 응답이다 (공통 명세 §3.1).
     *
     * 로그인 시작은 현재 임시 POST /api/auth/login 이다 (명세 §2.1, LocalLoginController).
     * 네이버 OAuth 복구 시에는 Spring Security 가 제공하는 GET /oauth2/authorization/naver 를 쓴다.
     */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: LoginUser?): MeResponse =
        authService.getCurrentUser(requireLogin(principal))

    /** 로그아웃. 세션을 버리고 SecurityContext 를 비운다. */
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
