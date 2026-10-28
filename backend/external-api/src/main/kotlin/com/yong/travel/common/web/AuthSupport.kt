package com.yong.travel.common.web

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode

/**
 * 로그인한 사용자 id 를 꺼낸다. 보안 설정이 잘못 열려도 막히도록 한 번 더 확인한다.
 *
 * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
 */
fun requireLogin(principal: LoginUser?): Long =
    principal?.userId ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
