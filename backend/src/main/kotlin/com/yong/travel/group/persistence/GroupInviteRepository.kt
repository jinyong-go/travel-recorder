package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.GroupInviteEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface GroupInviteRepository : JpaRepository<GroupInviteEntity, Long> {

    /**
     * 소유자가 보는 대기 초대 목록.
     *
     * 정원 판정이 수락 시점이라 정원을 넘겨 초대할 수 있어 건수 상한이 없다. 그래서 페이지로 끊는다 (명세 §4.8).
     */
    fun findByGroupIdOrderByCreatedAtAsc(groupId: Long, pageable: Pageable): Page<GroupInviteEntity>

    /** 받은 초대 목록. 나를 초대할 수 있는 그룹 수에 제한이 없어 역시 페이지로 끊는다 (명세 §4.8). */
    fun findByInviteeIdOrderByCreatedAtAsc(inviteeId: Long, pageable: Pageable): Page<GroupInviteEntity>

    /**
     * 내가 보낸 대기 초대를 그룹을 가로질러 모은다.
     *
     * 그룹을 조인해 소유자로 거르는 것과 결과가 같지만(소유자는 바뀌지 않는다) 조인 없이 인덱스
     * 하나로 끝나고, 남이 보낸 초대가 섞일 수 없어 인가가 조건 자체로 보장된다 (명세 §4.8).
     */
    fun findByInvitedByIdOrderByCreatedAtAsc(invitedById: Long, pageable: Pageable): Page<GroupInviteEntity>

    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInviteEntity?

    /** 그룹 삭제 시 이력으로 옮길 대상. 대기 초대는 정원과 무관하게 쌓일 수 있어 페이지로 끊지 않는다. */
    fun findByGroupId(groupId: Long): List<GroupInviteEntity>

    fun deleteByGroupId(groupId: Long)
}
