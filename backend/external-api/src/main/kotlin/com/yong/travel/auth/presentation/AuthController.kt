package com.yong.travel.auth.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.auth.service.AuthService
import com.yong.travel.auth.service.LoginHistoryService
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 세션 상태, 본인 정보, 로그인 이력, 로그아웃 API. */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val loginHistoryService: LoginHistoryService,
    private val csrfTokenRepository: CsrfTokenRepository,
) {

    /**
     * 로그인 여부와 CSRF 토큰을 돌려준다.
     * 토큰 쿠키는 브라우저 스크립트가 읽을 수 없고 로그인에도 토큰이 필요하므로 비로그인에게도 준다.
     */
    @GetMapping("/session")
    fun session(@AuthenticationPrincipal principal: LoginUser?, csrfToken: CsrfToken): SessionResponse =
        SessionResponse(authenticated = principal != null, csrfToken = csrfToken.token)

    /** 이메일을 포함한 본인 정보를 조회한다. */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: LoginUser?): MeResponse =
        MeResponse.from(authService.getCurrentUser(requireLogin(principal)))

    /**
     * 내 로그인 이력을 최신순으로 조회한다. 남의 이력을 지목할 수 없도록 사용자 id 를 받지 않는다.
     *
     * @param page 0부터 시작
     */
    @GetMapping("/me/login-history")
    fun loginHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<LoginHistoryResponse> =
        PageResponse.of(loginHistoryService.list(requireLogin(principal), page))
            .map { LoginHistoryResponse.from(it) }

    /** 세션을 끝낸다. CSRF 토큰은 세션과 따로 살아 있으므로 함께 비운다. */
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        csrfTokenRepository.saveToken(null, request, response)
        return ResponseEntity.noContent().build()
    }
}
