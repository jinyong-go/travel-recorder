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
 * OAuth 로그인 사용자를 불러와 `users` 에 upsert 한다.
 *
 * 네이버는 프로필을 최상위 `response` 객체로 감싸 주고, Google 은 평평한 속성 맵을 준다.
 * 네이버 응답을 풀어 두 제공자가 이후 단계에 같은 모양의 속성을 넘기게 한다.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 제공자 프로필로 계정을 만들거나 갱신한다.
     *
     * @param userRequest OAuth 제공자 등록 정보와 액세스 토큰
     * @return DB 사용자 PK 를 함께 실은 principal
     */
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
        log.debug("OAuth 로그인 userId={} provider={} newUser={}", savedUser.id, provider, existing == null)

        return CustomOAuth2User(
            DefaultOAuth2User(oAuth2User.authorities, attributes, "id"),
            requireNotNull(savedUser.id),
        )
    }

    /** 네이버 응답에서 실제 프로필이 담긴 `response` 객체를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun unwrapNaverResponse(oAuth2User: OAuth2User): Map<String, Any> =
        oAuth2User.attributes["response"] as Map<String, Any>
}
