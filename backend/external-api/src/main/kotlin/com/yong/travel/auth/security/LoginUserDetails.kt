package com.yong.travel.auth.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 고정 계정 로그인 주체. 인가는 소유자·그룹 멤버십으로 하므로 역할은 두지 않는다.
 */
class LoginUserDetails(
    private val username: String,
    private val password: String,
    override val userId: Long,
) : UserDetails, LoginUser {

    /** 역할이 없으므로 늘 비어 있다. */
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()

    override fun getPassword(): String = password

    override fun getUsername(): String = username
}
