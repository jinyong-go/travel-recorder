package com.yong.travel.trip.record.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 기록 조회와 여행 삭제에 딸린 벌크 soft delete. 목록용 일괄 조회(태그·기록 수)를 함께 둔다. */
interface TripRecordRepository : JpaRepository<TripRecordEntity, Long>, JpaSpecificationExecutor<TripRecordEntity> {

    /**
     * 여러 기록의 태그 이름을 한 번에 읽는다.
     *
     * 기록 목록이 기록마다 태그 컬렉션을 따로 읽지 않도록 두었다. 태그는 컬렉션이라 목록 쿼리에서
     * fetch 하면 페이징이 메모리에서 일어나므로 이렇게 따로 읽는다. 태그가 없는 기록은 결과에
     * 나오지 않으므로 호출부가 빈 목록으로 채운다.
     *
     * @param recordIds 기록 id 목록
     * @return 기록별 태그 이름 목록
     */
    @Query(
        """
        select new com.yong.travel.trip.record.persistence.RecordTagName(r.id, t.name)
        from TripRecordEntity r join r.tags t
        where r.id in :recordIds
        """,
    )
    fun findTagNamesByRecordIds(@Param("recordIds") recordIds: Collection<Long>): List<RecordTagName>

    /**
     * 여행별 살아 있는 기록 수.
     *
     * 목록 한 페이지(10건)마다 count 를 열 번 쏘지 않으려고 한 번에 묶는다.
     * 기록이 0건인 여행은 결과에 나오지 않으므로 호출부가 0으로 채운다.
     *
     * @param tripIds 여행 id 목록
     * @return 여행별 기록 수 목록
     */
    @Query(
        """
        select new com.yong.travel.trip.record.persistence.TripRecordCount(r.trip.id, count(r))
        from TripRecordEntity r
        where r.trip.id in :tripIds and r.deletedAt is null
        group by r.trip.id
        """,
    )
    fun countByTripIds(@Param("tripIds") tripIds: Collection<Long>): List<TripRecordCount>

    /**
     * 여행 삭제에 딸려 가는 하위 기록의 soft delete.
     *
     * 건건이 읽어 지우면 기록 수에 비례해 느려지므로 벌크 update 한 번으로 끝낸다.
     * 그래서 @SQLRestriction 이 걸리지 않아 `deletedAt is null` 조건을 직접 적는다 —
     * 이미 지워진 기록의 삭제 시각을 덮어쓰지 않기 위해서다.
     * 영속성 컨텍스트는 비우지 않는다(clearAutomatically 없음) — 호출부가 이어서 쓰는 여행 엔티티가
     * 준영속이 되면 그 삭제가 유실된다. 지워진 기록의 엔티티가 컨텍스트에 남아도 이후 쓰지 않는다.
     *
     * @param tripId 지운 여행 id
     * @param now 삭제 시각
     * @return 지운 기록 수
     */
    @Modifying(flushAutomatically = true)
    @Query("update TripRecordEntity r set r.deletedAt = :now where r.trip.id = :tripId and r.deletedAt is null")
    fun softDeleteByTripId(@Param("tripId") tripId: Long, @Param("now") now: Instant): Int
}
