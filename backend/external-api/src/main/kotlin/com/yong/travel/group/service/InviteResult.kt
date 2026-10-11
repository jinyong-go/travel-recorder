package com.yong.travel.group.service

import com.yong.travel.group.domain.GroupInvite

/**
 * 초대 보내기의 결과. 새로 만들어졌는지(`201`) 대기 중이던 초대를 그대로 돌려준 것인지(`200`)
 * 컨트롤러가 구분해야 한다.
 *
 * 초대가 끝난 방식을 뜻하는 [com.yong.travel.group.domain.InviteOutcome] 과는 관계가 없다.
 */
data class InviteResult(
    val invite: GroupInvite,
    val created: Boolean,
)
