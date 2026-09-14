package com.yong.travel.auth.repository

import com.yong.travel.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: String, providerId: String): User?
}
