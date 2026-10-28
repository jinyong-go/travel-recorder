package com.yong.travel.trip.record.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** 기록 저장소. */
interface TripRecordRepository : JpaRepository<TripRecordEntity, Long>, JpaSpecificationExecutor<TripRecordEntity> {

    /** 여러 기록의 태그 이름을 한 번에 읽는다. 태그가 없는 기록은 결과에 없다. */
    @Query(
        """
        select new com.yong.travel.trip.record.persistence.RecordTagName(r.id, t.name)
        from TripRecordEntity r join r.tags t
        where r.id in :recordIds
        """,
    )
    fun findTagNamesByRecordIds(@Param("recordIds") recordIds: Collection<Long>): List<RecordTagName>

    /** 여행별 기록 수를 한 번에 센다. 기록이 없는 여행은 결과에 없다. */
    @Query(
        """
        select new com.yong.travel.trip.record.persistence.TripRecordCount(r.trip.id, count(r))
        from TripRecordEntity r
        where r.trip.id in :tripIds and r.deletedAt is null
        group by r.trip.id
        """,
    )
    fun countByTripIds(@Param("tripIds") tripIds: Collection<Long>): List<TripRecordCount>

    /** 여행의 기록을 모두 삭제 상태로 바꾼다. 이미 지운 기록의 삭제 시각은 그대로 둔다. */
    @Modifying(flushAutomatically = true)
    @Query("update TripRecordEntity r set r.deletedAt = :now where r.trip.id = :tripId and r.deletedAt is null")
    fun softDeleteByTripId(@Param("tripId") tripId: Long, @Param("now") now: Instant): Int
}
