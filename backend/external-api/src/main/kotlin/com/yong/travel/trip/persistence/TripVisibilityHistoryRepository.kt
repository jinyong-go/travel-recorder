package com.yong.travel.trip.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 여행 공개 범위 이력. 조회 경로가 아직 없어 저장만 한다. 지우는 메서드를 두지 않는다. */
interface TripVisibilityHistoryRepository : JpaRepository<TripVisibilityHistoryEntity, Long>
