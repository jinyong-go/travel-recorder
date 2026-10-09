package com.yong.travel.trip.record.persistence

import com.yong.travel.trip.record.persistence.TripRecordEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TripRecordRepository : JpaRepository<TripRecordEntity, Long>, JpaSpecificationExecutor<TripRecordEntity> {

    /**
     * 기록 목록. 응답에 소속 여행 이름과 작성자(여행 소유자)가 실리므로 둘을 함께 읽는다 — 빠뜨리면
     * 처음 보는 여행·소유자마다 조회가 따로 나간다.
     *
     * 범위 판정이 Specification 에 있어 `@Query` fetch join 으로 옮길 수 없으므로 엔티티 그래프를 쓴다.
     * 엔티티 그래프는 건수 쿼리에 적용되지 않는다. 태그는 컬렉션이라 여기 넣으면 페이징이 메모리에서
     * 일어나므로 [findTagNamesByRecordIds] 로 따로 읽는다.
     */
    @EntityGraph(attributePaths = ["trip", "trip.owner"])
    override fun findAll(spec: Specification<TripRecordEntity>, pageable: Pageable): Page<TripRecordEntity>

    /**
     * 여러 기록의 태그 이름을 한 번에 읽는다. 결과는 `[기록 id, 태그 이름]` 쌍이다.
     *
     * 기록 목록이 기록마다 태그 컬렉션을 따로 읽지 않도록 두었다. 태그가 없는 기록은 결과에
     * 나오지 않으므로 호출부가 빈 목록으로 채운다.
     */
    @Query("select r.id, t.name from TripRecordEntity r join r.tags t where r.id in :recordIds")
    fun findTagNamesByRecordIds(@Param("recordIds") recordIds: Collection<Long>): List<Array<Any>>

    /**
     * 여행별 살아 있는 기록 수.
     *
     * 목록 한 페이지(10건)마다 count 를 열 번 쏘지 않으려고 한 번에 묶는다.
     * 기록이 0건인 여행은 결과에 나오지 않으므로 호출부가 0으로 채운다.
     */
    @Query(
        """
        select r.trip.id, count(r)
        from TripRecordEntity r
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
    @Query("update TripRecordEntity r set r.deletedAt = :now where r.trip.id = :tripId and r.deletedAt is null")
    fun softDeleteByTripId(@Param("tripId") tripId: Long, @Param("now") now: Instant): Int
}
