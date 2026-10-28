package com.yong.travel.trip.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** 여행 저장소. */
interface TripRepository : JpaRepository<TripEntity, Long>, JpaSpecificationExecutor<TripEntity> {

    /** 여행 목록을 소유자와 함께 읽는다. */
    @EntityGraph(attributePaths = ["owner"])
    override fun findAll(spec: Specification<TripEntity>, pageable: Pageable): Page<TripEntity>
}
