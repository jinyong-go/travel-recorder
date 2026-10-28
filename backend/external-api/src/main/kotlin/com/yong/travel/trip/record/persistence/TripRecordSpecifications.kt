package com.yong.travel.trip.record.persistence

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.common.persistence.PostgisFunctions
import com.yong.travel.common.util.LIKE_ESCAPE
import com.yong.travel.common.util.containsPattern
import com.yong.travel.common.util.trimToNull
import com.yong.travel.tag.persistence.TagEntity
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripVisibilityPredicates
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.GeoPoint
import com.yong.travel.trip.record.domain.RecordScope
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

/** 기록 조회 조건. 공개 범위는 소속 여행으로 판정한다. */
object TripRecordSpecifications {

    /** id 일치. */
    fun withId(recordId: Long): Specification<TripRecordEntity> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("id"), recordId) }

    /**
     * 요청자가 볼 수 있는 기록. 여행이 두 번 조인되므로 [withScope] 와 함께 쓰지 않는다.
     *
     * @param userId 비로그인이면 null 이며 전체 공개 여행의 기록만 해당한다
     */
    fun visibleTo(userId: Long?): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            val q = requireNotNull(query)
            TripVisibilityPredicates.visibleTo(joinTrip(root, q), q, cb, userId)
        }

    /**
     * 조회 범위 조건. 서비스가 여행 열람을 먼저 확인해도 이 판정은 빼지 않는다.
     *
     * @param scope null 이면 볼 수 있는 기록 전부다
     * @param userId 비로그인이면 null 이며 PUBLIC 만 조회된다
     */
    fun withScope(scope: RecordScope?, userId: Long?): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            val trip = joinTrip(root, requireNotNull(query))
            when (scope) {
                null -> TripVisibilityPredicates.visibleTo(trip, query, cb, userId)
                RecordScope.MINE -> TripVisibilityPredicates.ownedBy(trip, cb, userId)
                RecordScope.PUBLIC -> TripVisibilityPredicates.isPublic(trip, cb)
                RecordScope.SHARED -> TripVisibilityPredicates.sharedByOthersWith(trip, query, cb, userId)
            }
        }

    /**
     * 목록 필터. 비어 있는 조건은 걸지 않는다.
     *
     * @param tag 태그 이름 완전 일치
     * @param keyword 이름·주소 부분 일치
     */
    fun withFilters(
        tripId: Long?,
        category: Category?,
        tag: String?,
        keyword: String?,
    ): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            tripId?.let { predicates.add(cb.equal(root.get<TripEntity>("trip").get<Long>("id"), it)) }

            category?.let { predicates.add(cb.equal(root.get<Category>("category"), it)) }

            keyword.trimToNull()?.let {
                val like = containsPattern(it)
                predicates.add(
                    cb.or(
                        cb.like(root.get("name"), like, LIKE_ESCAPE),
                        cb.like(root.get("address"), like, LIKE_ESCAPE),
                    ),
                )
            }

            // 조인하면 기록이 태그 수만큼 늘어나므로 EXISTS 로 거른다.
            tag.trimToNull()?.let {
                val sub = requireNotNull(query).subquery(Long::class.java)
                val tagged = sub.correlate(root).join<TripRecordEntity, TagEntity>("tags")
                sub.select(cb.literal(1L)).where(cb.equal(tagged.get<String>("name"), it))
                predicates.add(cb.exists(sub))
            }

            cb.and(*predicates.toTypedArray())
        }

    /**
     * 기준 위치에서 가까운 순으로 정렬한다. 같은 거리면 최신순이다.
     * 페이지 요청에 정렬이 있으면 이 정렬을 덮어쓰므로 비워 둬야 한다.
     */
    fun orderByDistanceFrom(origin: GeoPoint): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            // 좌표가 매번 달라 SQL 에 박지 않고 바인딩한다.
            val hcb = cb as HibernateCriteriaBuilder
            val distance = cb.function(
                PostgisFunctions.KNN_DISTANCE,
                Double::class.java,
                root.get<Double>("latitude"),
                root.get<Double>("longitude"),
                hcb.value(origin.latitude),
                hcb.value(origin.longitude),
            )
            requireNotNull(query).orderBy(cb.asc(distance), cb.desc(root.get<Instant>("createdAt")))
            cb.conjunction()
        }

    /** 소속 여행을 조인한다. 목록은 여행·소유자를 함께 읽고, 건수 쿼리는 일반 조인을 쓴다. */
    @Suppress("UNCHECKED_CAST")
    private fun joinTrip(root: Root<TripRecordEntity>, query: CriteriaQuery<*>): Join<TripRecordEntity, TripEntity> {
        if (query.resultType == Long::class.javaObjectType) return root.join("trip")
        val trip = root.fetch<TripRecordEntity, TripEntity>("trip")
        trip.fetch<TripEntity, UserEntity>("owner")
        return trip as Join<TripRecordEntity, TripEntity>
    }
}
