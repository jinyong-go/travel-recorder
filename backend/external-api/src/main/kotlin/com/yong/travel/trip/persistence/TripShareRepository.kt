package com.yong.travel.trip.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 여행 공유 저장소. */
interface TripShareRepository : JpaRepository<TripShareEntity, Long> {

    /** 여러 여행의 공유를 그룹과 함께 한 번에 읽는다. */
    @Query("select s from TripShareEntity s join fetch s.group where s.trip.id in :tripIds")
    fun findByTripIdIn(@Param("tripIds") tripIds: Collection<Long>): List<TripShareEntity>

    /** 여행의 공유를 모두 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from TripShareEntity s where s.trip.id = :tripId")
    fun deleteByTripId(@Param("tripId") tripId: Long): Int

    /** 그룹에 걸린 공유를 모두 읽는다. */
    fun findByGroupId(groupId: Long): List<TripShareEntity>

    /** 사용자가 그룹에 건 공유를 읽는다. */
    fun findByGroupIdAndTripOwnerId(groupId: Long, ownerId: Long): List<TripShareEntity>
}
