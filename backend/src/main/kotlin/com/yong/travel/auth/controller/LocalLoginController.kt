package com.yong.travel.auth.controller

import com.yong.travel.auth.dto.MeResponse
import com.yong.travel.auth.dto.toMeResponse
import com.yong.travel.auth.security.LoginUser
import com.yong.travel.auth.service.AuthService
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 네이버 OAuth 가 준비되기 전까지 쓰는 임시 로그인 API (명세 §2.1).
 *
 * OAuth 복구 시 이 파일과 `LocalLoginConfig` 를 들어내면 된다. 요청 DTO 를 dto 패키지가 아니라
 * 여기에 둔 것도 같은 이유다 — 임시 코드를 한 파일에 모아 둔다.
 *
 * `LocalLoginConfig` 와 같은 프로파일 조건을 건다. 조건이 어긋나면 주입할
 * `AuthenticationManager` 가 없어 기동이 실패한다.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("local", "dev")
class LocalLoginController(
    private val authenticationManager: AuthenticationManager,
    private val authService: AuthService,
) {

    /** 세션 쿠키 인증이므로(공통 명세 §6.1) 인증 결과를 세션에 직접 넣는다. */
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    data class LocalLoginRequest(
        @field:NotBlank
        val username: String,

        @field:NotBlank
        val password: String,
    )

    /**
     * 아이디·비밀번호로 로그인하고 세션을 발급한다.
     *
     * 실패 사유(없는 계정 / 틀린 비밀번호)를 구분하지 않고 모두 `401 UNAUTHENTICATED` 로 답한다.
     * 구분해 주면 아이디 존재 여부가 드러난다.
     */
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LocalLoginRequest,
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

        // SecurityContextHolder 만 채우면 그 요청 안에서만 유효하다. 다음 요청에서도 로그인
        // 상태로 남으려면 세션에 저장해야 한다.
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        return authService.getCurrentUser((authentication.principal as LoginUser).userId).toMeResponse()
    }
}
