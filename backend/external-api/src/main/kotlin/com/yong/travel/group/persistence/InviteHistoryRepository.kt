package com.yong.travel.group.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 끝난 초대 이력 저장소. 이력은 지우지 않는다. */
interface InviteHistoryRepository : JpaRepository<InviteHistoryEntity, Long> {

    /** 받은 초대 이력을 끝난 시각의 최신순으로 읽는다. */
    @Query(
        value = """
            select h from InviteHistoryEntity h
            join fetch h.invitedBy
            where h.invitee.id = :inviteeId
            order by h.resolvedAt desc
        """,
        countQuery = "select count(h) from InviteHistoryEntity h where h.invitee.id = :inviteeId",
    )
    fun findByInviteeIdOrderByResolvedAtDesc(
        @Param("inviteeId") inviteeId: Long,
        pageable: Pageable,
    ): Page<InviteHistoryEntity>

    /** 보낸 초대 이력을 끝난 시각의 최신순으로 읽는다. */
    @Query(
        value = """
            select h from InviteHistoryEntity h
            join fetch h.invitee
            where h.invitedBy.id = :invitedById
            order by h.resolvedAt desc
        """,
        countQuery = "select count(h) from InviteHistoryEntity h where h.invitedBy.id = :invitedById",
    )
    fun findByInvitedByIdOrderByResolvedAtDesc(
        @Param("invitedById") invitedById: Long,
        pageable: Pageable,
    ): Page<InviteHistoryEntity>
}
