package com.yong.travel.common.web

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode

/**
 * 로그인한 사용자의 PK 를 꺼낸다. 로그인 여부는 SecurityConfig 가 먼저 막지만, 그 목록이 잘못
 * 열렸을 때도 인증이 필요한 엔드포인트가 비로그인으로 돌지 않도록 여기서 한 번 더 막는다.
 *
 * 로그인 수단(인메모리·네이버 OAuth)과 무관하게 동작하도록 principal 은 LoginUser 로 받는다.
 *
 * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
 */
fun requireLogin(principal: LoginUser?): Long =
    principal?.userId ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
