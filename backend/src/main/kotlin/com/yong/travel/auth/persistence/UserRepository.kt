package com.yong.travel.auth.persistence

import com.yong.travel.auth.persistence.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: String, providerId: String): User?

    /**
     * 초대 대상 조회. 완전 일치로만 찾고 부분 일치 검색은 제공하지 않는다 —
     * 이름·이메일 일부로 전체 가입자를 훑을 수 있게 되기 때문이다 (공통 명세 §3.7).
     */
    fun findByEmailIgnoreCase(email: String): User?
}
