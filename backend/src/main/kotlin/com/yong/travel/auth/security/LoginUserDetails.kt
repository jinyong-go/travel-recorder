package com.yong.travel.auth.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 인메모리 로그인 계정의 principal. 비밀번호 검증에 쓰이는 `UserDetails` 에
 * 우리 DB 의 `User` PK 를 얹어, 컨트롤러가 바로 사용자로 이어지게 한다 (명세 §2.1).
 *
 * 권한(role)은 두지 않는다. 인가 판정은 전부 소유자·그룹 멤버십으로 하므로(명세 §2.2)
 * 역할을 만들어도 판정에 쓰이지 않는다.
 */
class LoginUserDetails(
    private val username: String,
    private val password: String,
    override val userId: Long,
) : UserDetails, LoginUser {

    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()

    override fun getPassword(): String = password

    override fun getUsername(): String = username
}
