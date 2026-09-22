package com.yong.travel.common.web

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode

/**
 * SecurityConfig 가 아직 모든 요청을 permitAll 로 통과시키므로, 인증이 필요한 엔드포인트는
 * 컨트롤러에서 principal 유무로 직접 막는다. SecurityConfig 를 authenticated 로 되돌린 뒤에도
 * 사용자 PK 획득 경로로 그대로 쓰인다.
 *
 * 로그인 수단(인메모리·네이버 OAuth)과 무관하게 동작하도록 principal 은 LoginUser 로 받는다.
 */
fun requireLogin(principal: LoginUser?): Long =
    principal?.userId ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
