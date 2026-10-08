package com.yong.travel.trip.persistence

import com.yong.travel.trip.persistence.TripEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 여행 조회. 공개 범위 판정을 조회 쿼리에 실어 보내야 하므로 Specification 을 함께 쓴다
 * (명세 §2.2) — 전부 읽어 온 뒤 애플리케이션에서 걸러 내면 페이지 건수가 어긋난다.
 */
interface TripRepository : JpaRepository<TripEntity, Long>, JpaSpecificationExecutor<TripEntity> {

    /**
     * 여행 목록. 응답에 소유자 이름이 실리므로 소유자를 함께 읽는다 — 빠뜨리면 처음 보는 소유자마다
     * 조회가 따로 나간다.
     *
     * 범위 판정이 Specification 에 있어 `@Query` fetch join 으로 옮길 수 없으므로 엔티티 그래프를 쓴다.
     * 엔티티 그래프는 건수 쿼리에 적용되지 않아 페이지 건수 계산에 영향이 없다.
     */
    @EntityGraph(attributePaths = ["owner"])
    override fun findAll(spec: Specification<TripEntity>, pageable: Pageable): Page<TripEntity>
}
