package com.yong.travel.auth.dto

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.domain.User
import com.yong.travel.auth.persistence.UserEntity

/**
 * 다른 사용자에게도 노출되는 최소 정보. 이메일은 담지 않는다 —
 * 기록의 작성자나 그룹 멤버로 이름·프로필 사진이 보이는 것과, 연락처가 드러나는 것은 다른 이야기다.
 */
data class UserResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
)

/** 로그인 사용자 본인 정보. 이메일이 포함되는 유일한 응답이다. 제공 동의를 받지 못한 계정은 null 이다. */
data class MeResponse(
    val id: Long,
    val name: String,
    val email: String?,
    val profileImageUrl: String?,
)

fun UserEntity.toResponse(): UserResponse =
    UserResponse(
        id = requireNotNull(id),
        name = name,
        profileImageUrl = profileImageUrl,
    )

fun MyProfile.toMeResponse(): MeResponse = MeResponse(id, name, email, profileImageUrl)

/** 도메인 값 → 응답. 모양이 같아도 계층이 다르므로 변환을 거친다 ([User] 주석 참고). */
fun User.toResponse(): UserResponse = UserResponse(id, name, profileImageUrl)
