package com.yong.travel.group.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 대기 중인 초대의 조회와 삭제. 목록은 관점(그룹·받은 사람·보낸 사람)마다 따로 둔다. */
interface GroupInviteRepository : JpaRepository<GroupInviteEntity, Long> {

    /**
     * 소유자가 보는 대기 초대 목록.
     *
     * 정원을 넘겨 초대할 수 있어 건수 상한이 없으므로 페이지로 끊는다.
     *
     * 받는 사람·보낸 사람은 fetch join 하고, 그룹은 호출부가 권한 확인 때 읽어 두어 조인하지 않는다.
     * fetch join 쿼리로는 건수를 셀 수 없어 countQuery 를 따로 둔다 (아래 목록도 같다).
     *
     * @return 오래된 순의 대기 초대 한 페이지
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
     * 받은 초대 목록. 나를 초대할 수 있는 그룹 수에 제한이 없어 역시 페이지로 끊는다.
     *
     * 그룹·보낸 사람은 행마다 다르고 받는 사람(나)도 응답 변환에서 읽으므로 셋 다 fetch join 한다.
     *
     * @return 오래된 순의 받은 초대 한 페이지
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
     * 그룹 소유자 대신 보낸 사람으로 거른다. 결과는 같지만(소유자는 바뀌지 않는다) 인덱스 하나로 끝나고
     * 남이 보낸 초대가 섞일 수 없다. 응답에 쓰는 그룹·두 사람은 fetch join 한다.
     *
     * @return 오래된 순의 보낸 초대 한 페이지
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

    /**
     * 같은 그룹·같은 상대의 대기 초대를 찾는다. 유니크 제약상 많아야 하나다.
     *
     * @return 대기 초대. 없으면 null
     */
    fun findByGroupIdAndInviteeId(groupId: Long, inviteeId: Long): GroupInviteEntity?

    /**
     * 그룹 삭제 시 이력으로 옮길 대기 초대를 읽는다. 정원과 무관하게 쌓일 수 있지만 한 번에 옮겨야 해서
     * 페이지로 끊지 않는다.
     *
     * @return 그 그룹의 대기 초대 전부
     */
    fun findByGroupId(groupId: Long): List<GroupInviteEntity>

    /**
     * 그룹 삭제에 딸린 대기 초대 삭제. 호출부가 먼저 [findByGroupId] 로 읽어 이력으로 옮긴다.
     *
     * **다른 벌크 삭제와 달리 영속성 컨텍스트를 비운다.** 읽어 둔 초대가 남아 있으면 그룹 삭제 flush 가
     * 실패한다. 그룹은 준영속이 되어 호출부의 `groupRepository.delete` 가 다시 찾아 지운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupInviteEntity i where i.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: Long): Int
}
