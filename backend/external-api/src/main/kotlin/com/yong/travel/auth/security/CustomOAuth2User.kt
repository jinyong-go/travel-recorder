package com.yong.travel.auth.security

import org.springframework.security.oauth2.core.user.OAuth2User

/** 사용자 id 를 함께 실은 OAuth2 로그인 주체. */
class CustomOAuth2User(
    delegate: OAuth2User,
    override val userId: Long,
) : OAuth2User by delegate, LoginUser
