package com.yong.travel.auth.service

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/**
 * Naver wraps the profile payload under a top-level "response" object,
 * unlike Google which returns a flat attribute map. This unwraps it so
 * both providers expose the same flat attribute shape downstream.
 */
@Service
class UserService(
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        if (userRequest.clientRegistration.registrationId != "naver") {
            return oAuth2User
        }

        @Suppress("UNCHECKED_CAST")
        val response = oAuth2User.attributes["response"] as Map<String, Any>
        return DefaultOAuth2User(oAuth2User.authorities, response, "id")
    }
}
