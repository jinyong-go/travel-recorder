package com.yong.travel.group.service

import com.yong.travel.group.domain.GroupInvite

/** 초대 결과. 새로 만들어졌는지(`201`) 기존 대기 초대를 그대로 돌려준 것인지(`200`) 컨트롤러가 구분해야 한다. */
data class InviteResult(
    val invite: GroupInvite,
    val created: Boolean,
)
