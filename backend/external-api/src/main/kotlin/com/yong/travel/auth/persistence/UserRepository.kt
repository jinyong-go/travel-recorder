package com.yong.travel.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 사용자 조회. 로그인 upsert 와 초대 대상 찾기에 쓴다. */
interface UserRepository : JpaRepository<UserEntity, Long> {
    /**
     * 로그인 수단과 수단 안의 식별자로 계정을 찾는다. 동일인 판정의 기준이다.
     *
     * @param provider 로그인 수단 (`naver`, `local`)
     * @return 계정. 없으면 null
     */
    fun findByProviderAndProviderId(provider: String, providerId: String): UserEntity?

    /**
     * 초대 대상 조회. 완전 일치로만 찾고 부분 일치 검색은 제공하지 않는다 —
     * 이름·이메일 일부로 전체 가입자를 훑을 수 있게 되기 때문이다.
     *
     * @param email 찾을 이메일. 대소문자를 가리지 않는다
     * @return 계정. 없으면 null
     */
    fun findByEmailIgnoreCase(email: String): UserEntity?
}
