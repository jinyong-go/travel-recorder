package com.yong.travel.group.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 그룹 멤버 저장소. */
interface GroupMemberRepository : JpaRepository<GroupMemberEntity, Long> {

    /** 그룹에서 사용자의 멤버 행을 찾는다. 멤버가 아니면 null. */
    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMemberEntity?

    /** 사용자가 그룹 멤버인지 확인한다. */
    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean

    /** 소유자를 포함한 그룹 인원을 센다. */
    fun countByGroupId(groupId: Long): Long

    /** 여러 그룹의 멤버를 가입 순으로 한 번에 읽는다. */
    @Query(
        "select m from GroupMemberEntity m join fetch m.user " +
            "where m.group.id in :groupIds order by m.joinedAt asc",
    )
    fun findWithUserByGroupIdIn(@Param("groupIds") groupIds: Collection<Long>): List<GroupMemberEntity>

    /** 그룹의 멤버를 모두 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMemberEntity m where m.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: Long): Int

    /** 주어진 그룹 중 사용자가 속한 그룹만 읽는다. */
    @Query("select m.group from GroupMemberEntity m where m.group.id in :groupIds and m.user.id = :userId")
    fun findGroupsByIdInAndUserId(
        @Param("groupIds") groupIds: Collection<Long>,
        @Param("userId") userId: Long,
    ): List<GroupEntity>

    /** 사용자가 속한 그룹을 이름순으로 읽는다. 소유한 그룹도 포함된다. */
    @Query(
        value = "select m.group from GroupMemberEntity m where m.user.id = :userId order by m.group.name asc",
        countQuery = "select count(m) from GroupMemberEntity m where m.user.id = :userId",
    )
    fun findGroupsByUserId(@Param("userId") userId: Long, pageable: Pageable): Page<GroupEntity>

    /** 사용자가 소유하지 않고 참여만 한 그룹 수. */
    @Query("select count(m) from GroupMemberEntity m where m.user.id = :userId and m.group.owner.id <> :userId")
    fun countJoinedByUserId(@Param("userId") userId: Long): Long
}
