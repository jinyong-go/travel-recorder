package com.yong.travel.group.presentation

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 그룹 생성 요청. */
data class GroupCreateRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    @field:Size(max = 200)
    val memo: String? = null,
)

/** 그룹 수정 요청. 생성과 따로 바뀔 수 있어 타입을 나눈다. */
data class GroupUpdateRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    /** 빼고 보내면 메모가 지워진다. */
    @field:Size(max = 200)
    val memo: String? = null,
)

/** 초대 요청. */
data class InviteRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)
