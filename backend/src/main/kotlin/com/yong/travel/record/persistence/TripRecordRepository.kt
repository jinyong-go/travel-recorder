package com.yong.travel.record.persistence

import com.yong.travel.record.persistence.TripRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TripRecordRepository : JpaRepository<TripRecord, Long>, JpaSpecificationExecutor<TripRecord> {

    /**
     * 여행별 살아 있는 기록 수.
     *
     * 목록 한 페이지(10건)마다 count 를 열 번 쏘지 않으려고 한 번에 묶는다.
     * 기록이 0건인 여행은 결과에 나오지 않으므로 호출부가 0으로 채운다 (명세 §4.3.1).
     */
    @Query(
        """
        select r.trip.id, count(r)
        from TripRecord r
        where r.trip.id in :tripIds and r.deletedAt is null
        group by r.trip.id
        """,
    )
    fun countByTripIds(@Param("tripIds") tripIds: Collection<Long>): List<Array<Any>>

    /**
     * 여행 삭제에 딸려 가는 하위 기록의 soft delete.
     *
     * 건건이 읽어 지우면 기록 수에 비례해 느려지므로 벌크 update 한 번으로 끝낸다.
     * 그래서 @SQLRestriction 이 걸리지 않아 `deletedAt is null` 조건을 직접 적는다 —
     * 이미 지워진 기록의 삭제 시각을 덮어쓰지 않기 위해서다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TripRecord r set r.deletedAt = :now where r.trip.id = :tripId and r.deletedAt is null")
    fun softDeleteByTripId(@Param("tripId") tripId: Long, @Param("now") now: Instant): Int
}
