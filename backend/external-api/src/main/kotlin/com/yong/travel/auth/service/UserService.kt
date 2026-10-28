package com.yong.travel.auth.service

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.util.trimToNull
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** OAuth 로그인 사용자를 계정으로 등록하거나 갱신한다. 지금은 임시 로그인으로 대체되어 쓰이지 않는다. */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 제공자 프로필로 계정을 만들거나 갱신하고, 사용자 id 를 실은 주체를 돌려준다.
     */
    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val provider = userRequest.clientRegistration.registrationId
        val attributes = if (provider == "naver") unwrapNaverResponse(oAuth2User) else oAuth2User.attributes

        val providerId = attributes["id"].toString()
        // 빈 이메일은 null 로 둔다 ([UserEntity.email]).
        val email = (attributes["email"] as? String).trimToNull()
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
        log.debug("OAuth 로그인 userId={} provider={} newUser={}", savedUser.id, provider, existing == null)

        return CustomOAuth2User(
            DefaultOAuth2User(oAuth2User.authorities, attributes, "id"),
            requireNotNull(savedUser.id),
        )
    }

    /** 네이버는 프로필을 `response` 로 감싸 주므로 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun unwrapNaverResponse(oAuth2User: OAuth2User): Map<String, Any> =
        oAuth2User.attributes["response"] as Map<String, Any>
}
