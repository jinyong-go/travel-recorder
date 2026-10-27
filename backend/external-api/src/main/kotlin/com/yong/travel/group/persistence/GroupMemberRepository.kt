package com.yong.travel.group.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 그룹 멤버 행의 조회와 삭제. */
interface GroupMemberRepository : JpaRepository<GroupMemberEntity, Long> {

    /**
     * 한 그룹에서 특정 사용자의 멤버 행을 찾는다.
     *
     * @return 멤버 행. 멤버가 아니면 null
     */
    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMemberEntity?

    /**
     * 사용자가 그 그룹의 멤버인지 확인한다.
     *
     * @return 멤버면 true
     */
    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean

    /** 그룹의 현재 인원을 센다. 소유자도 포함된다. */
    fun countByGroupId(groupId: Long): Long

    /**
     * 여러 그룹의 멤버를 사용자와 함께 한 번에 읽는다. 가입 순서(joinedAt)대로다.
     *
     * 그룹 목록이 그룹마다 명단·건수를 따로 묻지 않도록 두었다. 사용자를 fetch join 해 멤버마다
     * 사용자 조회가 따로 나가지 않게 한다.
     *
     * @return 사용자를 함께 읽은 멤버 행. 가입 순이다
     */
    @Query(
        "select m from GroupMemberEntity m join fetch m.user " +
            "where m.group.id in :groupIds order by m.joinedAt asc",
    )
    fun findWithUserByGroupIdIn(@Param("groupIds") groupIds: Collection<Long>): List<GroupMemberEntity>

    /**
     * 그룹 삭제에 딸린 멤버 행 삭제.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMemberEntity m where m.group.id = :groupId")
    fun deleteByGroupId(@Param("groupId") groupId: Long): Int

    /**
     * 요청한 그룹 중 사용자가 소유자이든 초대로 들어왔든 속한 그룹만 읽는다. 공유 대상 그룹 검증이 쓴다.
     *
     * @return 속한 그룹 엔티티. 속하지 않았거나 없는 그룹은 빠진다
     */
    @Query("select m.group from GroupMemberEntity m where m.group.id in :groupIds and m.user.id = :userId")
    fun findGroupsByIdInAndUserId(
        @Param("groupIds") groupIds: Collection<Long>,
        @Param("userId") userId: Long,
    ): List<GroupEntity>

    /**
     * 사용자가 속한 그룹을 이름순으로 한 페이지 읽는다. 소유한 그룹도 멤버 행이 있어 함께 나온다.
     *
     * @return 이름순 그룹 한 페이지
     */
    @Query(
        value = "select m.group from GroupMemberEntity m where m.user.id = :userId order by m.group.name asc",
        countQuery = "select count(m) from GroupMemberEntity m where m.user.id = :userId",
    )
    fun findGroupsByUserId(@Param("userId") userId: Long, pageable: Pageable): Page<GroupEntity>

    /** 사용자가 소유하지 않고 멤버로만 속한 그룹 수. 참여 그룹 상한 판정에 쓴다. */
    @Query("select count(m) from GroupMemberEntity m where m.user.id = :userId and m.group.owner.id <> :userId")
    fun countJoinedByUserId(@Param("userId") userId: Long): Long
}
