package com.yong.travel.auth.security

import org.springframework.security.oauth2.core.user.OAuth2User

/** OAuth2 프로필에 우리 DB의 User PK를 함께 실어 컨트롤러에서 바로 쓸 수 있게 한다. */
class CustomOAuth2User(
    delegate: OAuth2User,
    override val userId: Long,
) : OAuth2User by delegate, LoginUser
