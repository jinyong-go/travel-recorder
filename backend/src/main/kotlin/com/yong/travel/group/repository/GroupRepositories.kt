package com.yong.travel.group.repository

import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.domain.InviteHistory
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRepository : JpaRepository<Group, Long> {

    /**
     * 정원 검사와 멤버 입력을 한 트랜잭션에 묶기 위해 그룹 행을 잠그고 읽는다.
     *
     * 정원을 강제하는 DB 제약이 없어 애플리케이션 검사가 유일한 관문인데, 검사와 입력 사이에
     * 다른 수락이 끼어들면 6명짜리 그룹이 만들어진다 (명세 §4.8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Group?
}

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {

    fun findByGroupIdOrderByJoinedAtAsc(groupId: Long): List<GroupMember>

    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMember?

    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean

    fun countByGroupId(groupId: Long): Long

    fun deleteByGroupId(groupId: Long)

    /** 사용자가 소유자이든 초대로 들어왔든, 속한 그룹 전부. 조회 권한 판정의 출발점이다. */
    @Query("select m.group.id from GroupMember m where m.user.id = :userId")
    fun findGroupIdsByUserId(@Param("userId") userId: Long): List<Long>

    @Query("select m.group from GroupMember m where m.user.id = :userId order by m.group.name asc")
    fun findGroupsByUserId(@Param("userId") userId: Long): List<Group>
}

interface GroupInviteRepository : JpaRepository<GroupInvite, Long> {

    /**
     * 소유자가 보는 대기 초대 목록.
     *
     * 정원 판정이 수락 시점이라 정원을 넘겨 초대할 수 있어 건수 상한이 없다. 그래서 페이지로 끊는다 (명세 §4.8).
     */
    fun findByGroupIdOrderByCreatedAtAsc(groupId: Long, pageable: Pageable): Page<GroupInvite>

    /** 받은 초대 목록. 나를 초대할 수 있는 그룹 수에 제한이 없어 역시 페이지로 끊는다 (명세 §4.8). */
    fun findByInviteeIdOrderByCreatedAtAsc(inviteeId: Long, pageable: Pageable): Page<GroupInvite>

    /**
     * 내가 보낸 대기 초대를 그룹을 가로질러 모은다.
     *
     * 그룹을 조인해 소유자로 거르는 것과 결과가 같지만(소유자는 바뀌지 않는다) 조인 없이 인덱스
     * 하나로 끝나고, 남이 보낸 초대가 섞일 수 없어 인가가 조건 자체로 보장된다 (명세 §4.8).
     */
    fun findByInvitedByIdOrderByCreatedAtAsc(invitedById: Long, pageable: Pageable): Page<GroupInvite>

    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInvite?

    /** 그룹 삭제 시 이력으로 옮길 대상. 대기 초대는 정원과 무관하게 쌓일 수 있어 페이지로 끊지 않는다. */
    fun findByGroupId(groupId: Long): List<GroupInvite>

    fun deleteByGroupId(groupId: Long)
}

/**
 * 끝난 초대의 기록. 읽기는 양쪽 당사자의 관점 두 가지뿐이며, 조건이 곧 본인 필터다 (명세 §4.8).
 *
 * 지우는 메서드를 두지 않는다 — 이력은 삭제 대상이 아니다 (명세 §3.2).
 */
interface InviteHistoryRepository : JpaRepository<InviteHistory, Long> {

    fun findByInviteeIdOrderByResolvedAtDesc(inviteeId: Long, pageable: Pageable): Page<InviteHistory>

    fun findByInvitedByIdOrderByResolvedAtDesc(invitedById: Long, pageable: Pageable): Page<InviteHistory>
}
