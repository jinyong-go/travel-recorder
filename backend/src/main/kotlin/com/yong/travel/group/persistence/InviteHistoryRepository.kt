package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.InviteHistoryEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 끝난 초대의 기록. 읽기는 양쪽 당사자의 관점 두 가지뿐이며, 조건이 곧 본인 필터다 (명세 §4.8).
 *
 * 지우는 메서드를 두지 않는다 — 이력은 삭제 대상이 아니다 (명세 §3.2).
 */
interface InviteHistoryRepository : JpaRepository<InviteHistoryEntity, Long> {

    fun findByInviteeIdOrderByResolvedAtDesc(inviteeId: Long, pageable: Pageable): Page<InviteHistoryEntity>

    fun findByInvitedByIdOrderByResolvedAtDesc(invitedById: Long, pageable: Pageable): Page<InviteHistoryEntity>
}
