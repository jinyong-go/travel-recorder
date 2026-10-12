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

/** 세션 상태·본인 정보·로그인 이력·로그아웃 API (`/api/auth`). 로그인은 [LocalLoginController] 에 있다. */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val loginHistoryService: LoginHistoryService,
    private val csrfTokenRepository: CsrfTokenRepository,
) {

    /**
     * 클라이언트 부팅용 세션 조회. 로그인 여부와 CSRF 토큰만 주며, 비로그인이어도 200 이다.
     *
     * 토큰 쿠키가 HttpOnly 라 SPA 가 직접 읽지 못하고, 로그인 요청도 토큰이 있어야 하므로
     * 비로그인에게도 토큰을 준다. 사용자 정보는 GET /me 의 몫이다.
     * 쿠키가 없으면 토큰을 읽는 이 시점에 새로 발급된다.
     *
     * @param principal 로그인 사용자. 비로그인이면 null
     * @param csrfToken 요청에 걸린 CSRF 토큰
     * @return 로그인 여부와 CSRF 토큰
     */
    @GetMapping("/session")
    fun session(@AuthenticationPrincipal principal: LoginUser?, csrfToken: CsrfToken): SessionResponse =
        SessionResponse(authenticated = principal != null, csrfToken = csrfToken.token)

    /**
     * 로그인한 본인 정보 조회. 이메일이 담기는 유일한 응답이다.
     *
     * 로그인 시작은 현재 임시 POST /api/auth/login 이다 (LocalLoginController).
     * 네이버 OAuth 복구 시에는 Spring Security 가 제공하는 GET /oauth2/authorization/naver 를 쓴다.
     *
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 이메일까지 담긴 본인 정보
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때, 또는 세션의 사용자 행이 없을 때
     */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: LoginUser?): MeResponse =
        MeResponse.from(authService.getCurrentUser(requireLogin(principal)))

    /**
     * 내 로그인 이력, 최신순.
     *
     * 경로에 사용자 id 를 받지 않는다. 대상은 언제나 세션의 사용자이므로 남의 이력을 지목할
     * 방법 자체가 없다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 로그인 이력 한 페이지
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     */
    @GetMapping("/me/login-history")
    fun loginHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<LoginHistoryResponse> =
        PageResponse.of(loginHistoryService.list(requireLogin(principal), page))
            .map { LoginHistoryResponse.from(it) }

    /**
     * 로그아웃. 세션을 버리고 SecurityContext 를 비운다.
     *
     * CSRF 토큰 쿠키도 비운다. 쿠키 토큰은 세션과 무관해서 비우지 않으면
     * 로그아웃 뒤에도 같은 값이 살아 있다. 새 토큰은 다음 세션 조회에서 발급된다.
     *
     * @param request 무효화할 세션이 걸린 요청
     * @param response 비운 CSRF 쿠키를 실을 응답
     * @return 본문 없는 `204`
     */
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        csrfTokenRepository.saveToken(null, request, response)
        return ResponseEntity.noContent().build()
    }
}
