package com.yong.travel.auth.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 인메모리 로그인 계정의 principal. 비밀번호 검증에 쓰이는 `UserDetails` 에
 * 우리 DB 의 `UserEntity` PK 를 얹어, 컨트롤러가 바로 사용자로 이어지게 한다.
 *
 * 권한(role)은 두지 않는다. 인가 판정은 전부 소유자·그룹 멤버십으로 하므로
 * 역할을 만들어도 판정에 쓰이지 않는다.
 */
class LoginUserDetails(
    private val username: String,
    private val password: String,
    override val userId: Long,
) : UserDetails, LoginUser {

    /** 권한 목록. 역할을 두지 않으므로 늘 비어 있다. */
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()

    /** 비밀번호 검증에 쓸 값. */
    override fun getPassword(): String = password

    /** 로그인 아이디. */
    override fun getUsername(): String = username
}
