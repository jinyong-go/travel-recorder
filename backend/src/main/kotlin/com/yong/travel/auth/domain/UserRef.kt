package com.yong.travel.auth.domain

/**
 * 타인에게 노출되는 최소 사용자 정보. 이름과 프로필 사진까지이며 이메일은 담지 않는다
 * (공통 명세 §3.1).
 *
 * 응답 DTO(`UserResponse`) 와 모양이 같지만 계층이 다르다. 이쪽은 서비스가 다루는 도메인 값이고,
 * 저쪽은 HTTP 로 나가는 표현이다. 한쪽을 바꿔야 할 때 다른 쪽이 끌려가지 않도록 나누어 둔다.
 */
data class UserRef(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
)
