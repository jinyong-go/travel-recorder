package com.yong.travel.auth.service

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.CustomOAuth2User
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val provider = userRequest.clientRegistration.registrationId
        val attributes = if (provider == "naver") unwrapNaverResponse(oAuth2User) else oAuth2User.attributes

        val providerId = attributes["id"].toString()
        // 빈 문자열로 채우면 이메일을 못 받은 계정이 둘째로 생기는 순간 유니크 제약에 걸린다 (UserEntity 참고).
        val email = (attributes["email"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val name = (attributes["name"] ?: attributes["nickname"]) as? String ?: providerId
        val profileImageUrl = attributes["profile_image"] as? String

        val existing = userRepository.findByProviderAndProviderId(provider, providerId)
        val user = existing
            ?.apply {
                this.email = email
                this.name = name
                this.profileImageUrl = profileImageUrl
            }
            ?: UserEntity(
                provider = provider,
                providerId = providerId,
                email = email,
                name = name,
                profileImageUrl = profileImageUrl,
            )
        val savedUser = userRepository.save(user)
        // 이메일·이름·providerId 는 남기지 않는다. 계정을 가리키는 값은 내부 id 하나로 충분하다.
        log.debug("로그인 userId={} provider={} 신규가입={}", savedUser.id, provider, existing == null)

        return CustomOAuth2User(
            DefaultOAuth2User(oAuth2User.authorities, attributes, "id"),
            requireNotNull(savedUser.id),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun unwrapNaverResponse(oAuth2User: OAuth2User): Map<String, Any> =
        oAuth2User.attributes["response"] as Map<String, Any>
}
