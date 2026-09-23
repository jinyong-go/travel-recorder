package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.Group
import com.yong.travel.group.persistence.GroupMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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
