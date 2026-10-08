package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.GroupInviteEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupInviteRepository : JpaRepository<GroupInviteEntity, Long> {

    /**
     * 소유자가 보는 대기 초대 목록.
     *
     * 정원 판정이 수락 시점이라 정원을 넘겨 초대할 수 있어 건수 상한이 없다. 그래서 페이지로 끊는다 (명세 §4.8).
     *
     * 받는 사람·보낸 사람을 fetch join 한다. 받는 사람은 행마다 달라, 빠뜨리면 행마다 사용자 조회가
     * 따로 나간다. 그룹은 호출부가 권한 확인 때 이미 읽어 두므로 조인하지 않는다.
     * fetch join 이 든 쿼리로는 건수를 셀 수 없어 countQuery 를 따로 둔다 (아래 목록도 같다).
     */
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

    /**
     * 받은 초대 목록. 나를 초대할 수 있는 그룹 수에 제한이 없어 역시 페이지로 끊는다 (명세 §4.8).
     *
     * 그룹·보낸 사람은 행마다 다르고 받는 사람(나)도 응답 변환에서 읽으므로 셋 다 fetch join 한다.
     */
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

    /**
     * 내가 보낸 대기 초대를 그룹을 가로질러 모은다.
     *
     * 그룹을 조인해 소유자로 거르는 것과 결과가 같지만(소유자는 바뀌지 않는다) 조인 없이 인덱스
     * 하나로 끝나고, 남이 보낸 초대가 섞일 수 없어 인가가 조건 자체로 보장된다 (명세 §4.8).
     * 아래 fetch join 은 결과를 거르지 않고 응답에 쓸 그룹·사람을 함께 읽기 위한 것이라 이 판단과 무관하다.
     *
     * 그룹·받는 사람은 행마다 다르고 보낸 사람(나)도 응답 변환에서 읽으므로 셋 다 fetch join 한다.
     */
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

    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInviteEntity?

    /** 그룹 삭제 시 이력으로 옮길 대상. 대기 초대는 정원과 무관하게 쌓일 수 있어 페이지로 끊지 않는다. */
    fun findByGroupId(groupId: Long): List<GroupInviteEntity>

    /**
     * 그룹 삭제에 딸린 대기 초대 삭제. 호출부가 먼저 [findByGroupId] 로 읽어 이력으로 옮긴다.
     *
     * 이름 기반 삭제(`deleteBy…`)는 행을 먼저 읽어 와 한 건씩 DELETE 하므로 벌크 삭제로 둔다.
     * **다른 벌크 삭제와 달리 영속성 컨텍스트를 비운다.** 호출부가 읽어 둔 초대 엔티티가 컨텍스트에
     * 남은 채 그룹 엔티티를 지우면, 지워진 그룹을 가리키는 초대 때문에 flush 가 실패한다.
     * 그룹은 이 호출 뒤 준영속이 되며, 호출부의 `groupRepository.delete` 가 다시 찾아 지운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupInviteEntity i where i.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: Long): Int
}
