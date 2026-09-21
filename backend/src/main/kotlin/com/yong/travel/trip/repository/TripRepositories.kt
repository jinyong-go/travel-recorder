package com.yong.travel.trip.repository

import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripShare
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 여행 조회. 공개 범위 판정을 조회 쿼리에 실어 보내야 하므로 Specification 을 함께 쓴다
 * (명세 §2.2) — 전부 읽어 온 뒤 애플리케이션에서 걸러 내면 페이지 건수가 어긋난다.
 */
interface TripRepository : JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip>

interface TripShareRepository : JpaRepository<TripShare, Long> {

    fun findByTripId(tripId: Long): List<TripShare>

    fun existsByTripIdAndGroupIdIn(tripId: Long, groupIds: Collection<Long>): Boolean

    fun deleteByTripId(tripId: Long)

    /** 그룹이 사라지면 그 그룹으로 공유되던 관계도 함께 사라진다 (명세 §3.1). */
    fun deleteByGroupId(groupId: Long)
}
