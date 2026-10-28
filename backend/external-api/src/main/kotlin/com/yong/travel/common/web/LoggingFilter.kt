package com.yong.travel.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청마다 메서드·경로·상태·소요 시간을 한 줄로 남긴다.
 * 이메일·세션 쿠키가 새지 않도록 본문과 헤더는 남기지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 요청을 처리한 뒤 한 줄을 남긴다. */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 예외로 끝난 요청도 남긴다.
            log.info(
                "{} {}{} -> {} ({}ms)",
                request.method,
                request.requestURI,
                queryOf(request),
                response.status,
                (System.nanoTime() - startedAt) / 1_000_000,
            )
        }
    }

    /** 파일 요청은 수가 많아 남기지 않는다. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith(FILE_SERVING_PREFIX)

    /**
     * 로그에 남길 쿼리 문자열. OAuth 인가 코드는 자격 증명이라 통째로 빼고,
     * 저장하지 않기로 한 기준 좌표는 값만 가린다.
     */
    private fun queryOf(request: HttpServletRequest): String {
        if (request.requestURI.startsWith("/oauth2/") || request.requestURI.startsWith("/login/oauth2/")) {
            return ""
        }
        val query = request.queryString ?: return ""
        return "?" + query.split("&").joinToString("&") { param ->
            val name = param.substringBefore('=')
            if (name in MASKED_PARAMS) "$name=***" else param
        }
    }

    private companion object {
        /** 값을 가릴 쿼리 파라미터(기준 위치). */
        val MASKED_PARAMS = setOf("lat", "lng")

        const val FILE_SERVING_PREFIX = "/api/files/"
    }
}
