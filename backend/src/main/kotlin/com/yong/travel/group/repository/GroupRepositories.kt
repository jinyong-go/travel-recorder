package com.yong.travel.group.repository

import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
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

    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInvite?

    fun deleteByGroupId(groupId: Long)
}
