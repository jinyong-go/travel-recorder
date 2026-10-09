package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.InviteHistoryEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 끝난 초대의 기록. 읽기는 양쪽 당사자의 관점 두 가지뿐이며, 조건이 곧 본인 필터다.
 *
 * 지우는 메서드를 두지 않는다 — 이력은 삭제 대상이 아니다.
 */
interface InviteHistoryRepository : JpaRepository<InviteHistoryEntity, Long> {

    /**
     * 받은 이력. 응답의 상대(counterpart)가 보냈던 사람이라 그쪽만 fetch join 한다 — 행마다 달라
     * 빠뜨리면 행마다 사용자 조회가 따로 나간다. 받은 사람(나)은 응답에 쓰지 않는다.
     * fetch join 이 든 쿼리로는 건수를 셀 수 없어 countQuery 를 따로 둔다.
     */
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

    /** 보낸 이력. 상대가 초대받았던 사람이라 그쪽만 fetch join 한다. 이유는 받은 이력과 같다. */
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
