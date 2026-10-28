package com.yong.travel.group.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 대기 중인 초대 저장소. */
interface GroupInviteRepository : JpaRepository<GroupInviteEntity, Long> {

    /** 그룹의 대기 초대를 오래된 순으로 읽는다. */
    @Query(
        value = """
            select i from GroupInviteEntity i
            join fetch i.invitee
            join fetch i.invitedBy
            where i.group.id = :groupId
            order by i.createdAt asc
        """,
        countQuery = "select count(i) from GroupInviteEntity i where i.group.id = :groupId",
    )
    fun findByGroupIdOrderByCreatedAtAsc(@Param("groupId") groupId: Long, pageable: Pageable): Page<GroupInviteEntity>

    /** 받은 초대를 오래된 순으로 읽는다. */
    @Query(
        value = """
            select i from GroupInviteEntity i
            join fetch i.group
            join fetch i.invitee
            join fetch i.invitedBy
            where i.invitee.id = :inviteeId
            order by i.createdAt asc
        """,
        countQuery = "select count(i) from GroupInviteEntity i where i.invitee.id = :inviteeId",
    )
    fun findByInviteeIdOrderByCreatedAtAsc(
        @Param("inviteeId") inviteeId: Long,
        pageable: Pageable,
    ): Page<GroupInviteEntity>

    /** 내가 보낸 대기 초대를 모든 그룹에 걸쳐 오래된 순으로 읽는다. */
    @Query(
        value = """
            select i from GroupInviteEntity i
            join fetch i.group
            join fetch i.invitee
            join fetch i.invitedBy
            where i.invitedBy.id = :invitedById
            order by i.createdAt asc
        """,
        countQuery = "select count(i) from GroupInviteEntity i where i.invitedBy.id = :invitedById",
    )
    fun findByInvitedByIdOrderByCreatedAtAsc(
        @Param("invitedById") invitedById: Long,
        pageable: Pageable,
    ): Page<GroupInviteEntity>

    /** 같은 그룹·같은 상대의 대기 초대를 찾는다. 없으면 null. */
    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInviteEntity?

    /** 그룹의 대기 초대를 모두 읽는다. 그룹을 지울 때 이력으로 옮기는 데 쓴다. */
    fun findByGroupId(groupId: Long): List<GroupInviteEntity>

    /**
     * 그룹의 대기 초대를 모두 지운다.
     * 읽어 둔 초대가 남아 있으면 그룹 삭제가 실패하므로 영속성 컨텍스트를 비운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupInviteEntity i where i.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: Long): Int
}
