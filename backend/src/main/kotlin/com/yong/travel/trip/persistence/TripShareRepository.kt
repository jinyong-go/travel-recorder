package com.yong.travel.trip.persistence

import com.yong.travel.trip.persistence.TripShare
import org.springframework.data.jpa.repository.JpaRepository

interface TripShareRepository : JpaRepository<TripShare, Long> {

    fun findByTripId(tripId: Long): List<TripShare>

    /**
     * 여러 여행의 공유 행을 한 번에 읽는다.
     *
     * 목록 응답이 여행마다 공유 그룹을 따로 묻지 않도록 두었다. 페이지 크기만큼 쿼리가 늘던
     * 자리이며, 공유 그룹은 소유자 본인의 항목에만 필요하므로 그 여행 id 만 넘어온다.
     */
    fun findByTripIdIn(tripIds: Collection<Long>): List<TripShare>

    fun existsByTripIdAndGroupIdIn(tripId: Long, groupIds: Collection<Long>): Boolean

    fun deleteByTripId(tripId: Long)

    /** 그룹이 사라지면 그 그룹으로 공유되던 관계도 함께 사라진다 (명세 §3.1). */
    fun deleteByGroupId(groupId: Long)
}
