package com.yong.travel.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 사용자 저장소. */
interface UserRepository : JpaRepository<UserEntity, Long> {
    /**
     * 로그인 수단과 수단 안의 id 로 계정을 찾는다. 없으면 null.
     *
     * @param provider `naver`, `local`
     */
    fun findByProviderAndProviderId(provider: String, providerId: String): UserEntity?

    /**
     * 이메일로 초대 대상을 찾는다. 대소문자는 가리지 않고, 없으면 null.
     * 부분 일치로 가입자를 훑지 못하도록 완전 일치로만 찾는다.
     */
    fun findByEmailIgnoreCase(email: String): UserEntity?
}
