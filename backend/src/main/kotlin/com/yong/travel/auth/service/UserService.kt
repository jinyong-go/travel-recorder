package com.yong.travel.auth.service

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.auth.security.CustomOAuth2User
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Naver wraps the profile payload under a top-level "response" object,
 * unlike Google which returns a flat attribute map. This unwraps it so
 * both providers expose the same flat attribute shape downstream.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val provider = userRequest.clientRegistration.registrationId
        val attributes = if (provider == "naver") unwrapNaverResponse(oAuth2User) else oAuth2User.attributes

        val providerId = attributes["id"].toString()
        val email = attributes["email"] as? String ?: ""
        val name = (attributes["name"] ?: attributes["nickname"]) as? String ?: providerId
        val profileImageUrl = attributes["profile_image"] as? String

        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?.apply {
                this.email = email
                this.name = name
                this.profileImageUrl = profileImageUrl
            }
            ?: User(
                provider = provider,
                providerId = providerId,
                email = email,
                name = name,
                profileImageUrl = profileImageUrl,
            )
        val savedUser = userRepository.save(user)

        return CustomOAuth2User(
            DefaultOAuth2User(oAuth2User.authorities, attributes, "id"),
            requireNotNull(savedUser.id),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun unwrapNaverResponse(oAuth2User: OAuth2User): Map<String, Any> =
        oAuth2User.attributes["response"] as Map<String, Any>
}
