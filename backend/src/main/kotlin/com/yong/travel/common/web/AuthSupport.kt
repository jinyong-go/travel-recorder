package com.yong.travel.common.web

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode

/**
 * SecurityConfig 가 아직 모든 요청을 permitAll 로 통과시키므로, 인증이 필요한 엔드포인트는
 * 컨트롤러에서 principal 유무로 직접 막는다. SecurityConfig 를 authenticated 로 되돌린 뒤에도
 * 사용자 PK 획득 경로로 그대로 쓰인다.
 */
fun requireLogin(principal: CustomOAuth2User?): Long =
    principal?.userId ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
