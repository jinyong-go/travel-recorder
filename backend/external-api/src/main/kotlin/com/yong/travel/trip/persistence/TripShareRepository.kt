package com.yong.travel.trip.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 여행 ↔ 그룹 공유 행의 조회와 벌크 삭제. */
interface TripShareRepository : JpaRepository<TripShareEntity, Long> {

    /**
     * 여러 여행의 공유 행을 한 번에 읽는다.
     *
     * 목록 응답이 여행마다 공유 그룹을 따로 묻지 않도록 두었다. 페이지 크기만큼 쿼리가 늘던
     * 자리이며, 공유 그룹은 소유자 본인의 항목에만 필요하므로 그 여행 id 만 넘어온다.
     *
     * 응답에 그룹 이름이 실리므로 그룹을 fetch join 한다. 빠뜨리면 공유 행은 한 번에 읽어도
     * 그룹마다 조회가 따로 나간다.
     *
     * @return 그룹을 함께 읽은 공유 행
     */
    @Query("select s from TripShareEntity s join fetch s.group where s.trip.id in :tripIds")
    fun findByTripIdIn(@Param("tripIds") tripIds: Collection<Long>): List<TripShareEntity>

    /**
     * 한 여행의 공유 행을 모두 지운다. 공유 그룹 교체와 여행 삭제가 쓴다.
     *
     * 이름 기반 삭제(`deleteBy…`)는 행을 먼저 읽어 와 한 건씩 DELETE 하므로 벌크 삭제로 둔다.
     * 영속성 컨텍스트는 비우지 않는다(clearAutomatically 없음) — 호출부가 이어서 쓰는 엔티티가
     * 준영속이 되면 그 뒤의 변경이 유실된다. 지워진 행의 엔티티가 컨텍스트에 남아도 이후 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from TripShareEntity s where s.trip.id = :tripId")
    fun deleteByTripId(@Param("tripId") tripId: Long): Int

    /** 한 그룹으로 걸린 공유 행 전부. 그룹 삭제가 공유를 풀기 전에 이력을 남기려고 읽는다. */
    fun findByGroupId(groupId: Long): List<TripShareEntity>

    /** 한 그룹에 특정 사용자가 걸어 둔 공유 행. 탈퇴·제외가 나간 사람의 공유만 풀 때 쓴다. */
    fun findByGroupIdAndTripOwnerId(groupId: Long, ownerId: Long): List<TripShareEntity>
}
