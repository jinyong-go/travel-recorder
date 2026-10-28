package com.yong.travel.auth.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.auth.service.AuthService
import com.yong.travel.auth.service.LoginHistoryService
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 네이버 OAuth 를 대신하는 임시 로그인 API.
 * 나중에 쉽게 걷어 내도록 요청 DTO 도 이 파일에 둔다.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("local", "dev")
class LocalLoginController(
    private val authenticationManager: AuthenticationManager,
    private val authService: AuthService,
    private val loginHistoryService: LoginHistoryService,
    private val csrfTokenRepository: CsrfTokenRepository,
) {

    /** 인증 결과를 세션에 저장한다. */
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    /** 고정 계정의 아이디와 비밀번호. */
    data class LocalLoginRequest(
        @field:NotBlank
        val username: String,

        @field:NotBlank
        val password: String,
    )

    /**
     * 아이디와 비밀번호로 로그인한다.
     * 아이디가 있는지 드러나지 않도록 실패 사유를 구분하지 않는다.
     */
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LocalLoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password),
            )
        } catch (e: AuthenticationException) {
            throw ApiException(ErrorCode.UNAUTHENTICATED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        val userId = (authentication.principal as LoginUser).userId

        // 이력 없는 로그인이 생기지 않도록 세션보다 먼저 남긴다.
        loginHistoryService.record(userId, httpRequest.remoteAddr, httpRequest.getHeader(HttpHeaders.USER_AGENT))

        // 직접 인증하면 세션 고정 방지가 돌지 않으므로 세션 id 와 CSRF 토큰을 직접 바꾼다.
        httpRequest.getSession(false)?.let { httpRequest.changeSessionId() }
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse)

        // 다음 요청에도 로그인 상태가 이어지도록 세션에 저장한다.
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        return MeResponse.from(authService.getCurrentUser(userId))
    }
}
