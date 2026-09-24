package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.persistence.GroupMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMemberRepository : JpaRepository<GroupMemberEntity, Long> {

    fun findByGroupIdOrderByJoinedAtAsc(groupId: Long): List<GroupMemberEntity>

    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMemberEntity?

    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean

    fun countByGroupId(groupId: Long): Long

    fun deleteByGroupId(groupId: Long)

    /** 사용자가 소유자이든 초대로 들어왔든, 속한 그룹 전부. 조회 권한 판정의 출발점이다. */
    @Query("select m.group.id from GroupMemberEntity m where m.user.id = :userId")
    fun findGroupIdsByUserId(@Param("userId") userId: Long): List<Long>

    @Query("select m.group from GroupMemberEntity m where m.user.id = :userId order by m.group.name asc")
    fun findGroupsByUserId(@Param("userId") userId: Long): List<GroupEntity>
}
