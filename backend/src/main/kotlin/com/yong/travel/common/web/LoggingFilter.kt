package com.yong.travel.common.web

import com.yong.travel.photo.storage.FileSystemPhotoStorageService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 한 건을 끝난 뒤 한 줄로 남긴다 — 메서드·경로·상태·소요 시간.
 *
 * 시작과 끝을 두 줄로 나누지 않는다. 두 줄이 되면 동시 요청에서 짝을 잇기 위해 요청 id 와
 * MDC 가 필요해지는데, 끝난 뒤 한 줄이면 같은 정보를 id 없이 담는다.
 *
 * **본문·헤더는 남기지 않는다.** 초대 요청 본문에 이메일이(공통 명세 §3.1), 응답 헤더에
 * 세션 쿠키가 실린다. 명세가 응답에서 가리기로 한 값이 로그로 새는 경로를 만들지 않는다.
 * 본문 버퍼링(`ContentCaching*Wrapper`)이 사진 업로드에서 요청당 수십 MB를 힙에 올리는
 * 문제도 함께 피한다.
 *
 * 요청자 id 는 여기서 찍지 않는다. 이 필터는 보안 필터 체인 바깥이라 차단된 요청까지 남길 수
 * 있는 대신, 이 시점의 `SecurityContextHolder` 는 이미 비워져 있다. 누가 무엇을 했는지는
 * 서비스 로그가 맡는다.
 *
 * 레벨은 `INFO` 다. 도메인 서비스 로그(`DEBUG`)가 prod 에서 걸러지는 것과 달리, 이 한 줄은
 * 어떤 요청이 언제 어떻게 끝났는지를 남기는 운영 기록이라 모든 프로파일에서 남아야 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 예외로 끝난 요청도 남아야 하므로 finally 에서 찍는다. 이 시점의 상태 코드는
            // GlobalExceptionHandler 가 이미 채운 값이다.
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

    /** 정적 사진 서빙과 H2 콘솔은 건당 한 줄을 남길 값어치가 없다. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith(FileSystemPhotoStorageService.PHOTO_URL_PREFIX) ||
            request.requestURI.startsWith("/h2-console")

    /**
     * 쿼리 문자열. 두 가지를 걸러 낸다.
     *
     * OAuth 경로의 쿼리는 통째로 생략한다 — 인가 코드와 state 뿐이라 개발 중에도 볼 값어치가
     * 없고, 남으면 그 자체가 자격 증명이다 (명세 §2.1).
     *
     * 기준 좌표는 값만 가린다. 계산에만 쓰고 사용자와 묶어 저장하지 않기로 한 값이라(명세 §7)
     * 로그가 대신 보관하게 두지 않는다. 좌표가 함께 왔는지는 디버깅에 필요하므로 이름은 남긴다.
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
        /** 값을 가릴 쿼리 파라미터 — 요청자의 기준 위치다 (명세 §7). */
        val MASKED_PARAMS = setOf("lat", "lng")
    }
}
