package com.yong.travel.trip.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 여행 공개 범위 이력 저장소. 아직 조회하는 곳은 없다. */
interface TripVisibilityHistoryRepository : JpaRepository<TripVisibilityHistoryEntity, Long>
