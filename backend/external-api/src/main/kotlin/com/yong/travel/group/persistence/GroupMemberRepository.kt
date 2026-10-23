package com.yong.travel.group.persistence

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
     *
     * 이름 기반 삭제(`deleteBy…`)는 행을 먼저 읽어 와 한 건씩 DELETE 하므로 벌크 삭제로 둔다.
     * 영속성 컨텍스트는 비우지 않는다(clearAutomatically 없음) — 호출부가 이어서 쓰는 엔티티가
     * 준영속이 되면 그 뒤의 변경이 유실된다. 지워진 행의 엔티티가 컨텍스트에 남아도 이후 쓰지 않는다.
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

    /** 사용자가 속한 그룹을 이름순으로 읽는다. */
    @Query("select m.group from GroupMemberEntity m where m.user.id = :userId order by m.group.name asc")
    fun findGroupsByUserId(@Param("userId") userId: Long): List<GroupEntity>
}
