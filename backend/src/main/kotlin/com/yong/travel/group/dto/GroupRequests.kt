package com.yong.travel.group.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 그룹 생성 요청. */
data class GroupCreateRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    /** 생략하면 "메모 없음" 으로 저장된다. */
    @field:Size(max = 200)
    val memo: String? = null,
)

/**
 * 그룹 수정 요청.
 *
 * 생성 요청과 필드가 같지만 타입을 나눈다. 한쪽 화면의 입력 항목이 바뀌었을 때 다른 쪽까지
 * 끌려가지 않도록 하기 위해서다 — 여행·기록도 같은 방식이다 (`TripCreateRequest`/`TripUpdateRequest`).
 */
data class GroupUpdateRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    /** **이름과 함께 덮어쓴다.** 빼고 보내면 기존 메모가 지워진다 (명세 §4.7). */
    @field:Size(max = 200)
    val memo: String? = null,
)

data class InviteRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)
