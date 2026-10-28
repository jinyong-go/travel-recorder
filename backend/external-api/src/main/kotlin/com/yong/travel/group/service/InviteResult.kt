package com.yong.travel.group.service

import com.yong.travel.group.domain.GroupInvite

/** 초대 보내기 결과. [created] 는 새로 만든 초대인지를 뜻한다. */
data class InviteResult(
    val invite: GroupInvite,
    val created: Boolean,
)
