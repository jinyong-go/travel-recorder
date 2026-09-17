package com.yong.travel.group.repository

import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.domain.VisitRecordShare
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRepository : JpaRepository<Group, Long>

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

    fun findByGroupId(groupId: Long): GroupInvite?

    fun findByToken(token: String): GroupInvite?

    fun deleteByGroupId(groupId: Long)
}

interface VisitRecordShareRepository : JpaRepository<VisitRecordShare, Long> {

    fun findByRecordId(recordId: Long): List<VisitRecordShare>

    fun existsByRecordIdAndGroupIdIn(recordId: Long, groupIds: Collection<Long>): Boolean

    fun deleteByRecordId(recordId: Long)

    fun deleteByGroupId(groupId: Long)
}
