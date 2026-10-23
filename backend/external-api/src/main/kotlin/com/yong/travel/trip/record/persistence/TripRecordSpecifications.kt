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

/** 기록 조회의 동적 조건. 목록과 단건 모두 공개 범위 판정이 소속 여행 조인과 함께 여기서 쿼리에 실린다. */
object TripRecordSpecifications {

    /** id 일치 조건. 단건 조회를 [visibleTo] 와 묶는다. */
    fun withId(recordId: Long): Specification<TripRecordEntity> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("id"), recordId) }

    /**
     * 요청자가 볼 수 있는 기록 — 소속 여행을 볼 수 있는 기록이다. 단건 상세도 목록과 같은 판정식을 거친다.
     *
     * 소속 여행을 조인하므로 [withScope] 와 함께 쓰지 않는다. 함께 쓰면 trips 가 두 번 붙는다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 전체 공개 여행의 기록만 참이다
     */
    fun visibleTo(userId: Long?): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            val q = requireNotNull(query)
            TripVisibilityPredicates.visibleTo(joinTrip(root, q), q, cb, userId)
        }

    /**
     * 조회 범위 조건.
     *
     * **판정 대상은 기록이 아니라 소속 여행이다**. 기록에는 공개 범위도 작성자도
     * 없으므로 여행을 조인하지 않으면 판정할 근거가 없다. 판정식 자체는 [TripVisibilityPredicates] 에 있다.
     *
     * 공개 범위 판정을 조회 쿼리에 실어 보내는 것이 핵심이다. 전부 읽어 온 뒤 애플리케이션에서
     * 걸러 내면 페이지네이션 건수가 어긋나고, 거르는 걸 한 번 빠뜨리는 순간 곧바로 정보 유출이 된다.
     *
     * 범위와 상관없이 소속 여행을 조인하며, 목록 쿼리에서는 그 조인으로 여행과 소유자를 함께 읽는다
     * (응답에 여행 이름과 작성자가 실린다). 범위 조건과 fetch 가 조인을 따로 만들면 trips 가 두 번 붙는다.
     * 조인 자체가 삭제 필터 역할도 한다 — TripEntity 의 @SQLRestriction 덕에 삭제된 여행의 기록은
     * 조인 단계에서 함께 사라진다.
     *
     * @param scope 조회 범위. null 이면 요청자가 볼 수 있는 기록 전부다 — `tripId` 로 여행 하나를
     *        좁힐 때 쓴다. 서비스가 그 여행을 볼 수 있는지 먼저 확인하더라도 판정을 쿼리에서 빼지 않는다.
     *        사전 확인 한 줄에 안전을 맡기면, 그 줄이 빠지는 순간 남의 비공개 여행 기록이 전부 나간다
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며, 이 경우 PUBLIC 만 조회된다
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
     * 목록 필터.
     *
     * `tripId` 는 여행 상세 화면이 하위 기록을 가져올 때 쓴다. 범위 조건과 AND 로 묶이므로,
     * 볼 수 없는 여행의 id 를 넣어도 결과가 새지 않고 빈 목록이 된다.
     *
     * @param tripId 이 여행의 하위 기록으로 한정한다. null 이면 걸지 않는다
     * @param category 카테고리. null 이면 걸지 않는다
     * @param tag 태그 이름 완전 일치. 비었으면 걸지 않는다
     * @param keyword 이름·주소 부분 일치 검색어. 비었으면 걸지 않는다
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

            // 조인으로 거르면 태그 행만큼 기록이 불어나 DISTINCT 가 필요해진다. EXISTS 는 기록 행을 늘리지 않는다.
            tag.trimToNull()?.let {
                val sub = requireNotNull(query).subquery(Long::class.java)
                val tagged = sub.correlate(root).join<TripRecordEntity, TagEntity>("tags")
                sub.select(cb.literal(1L)).where(cb.equal(tagged.get<String>("name"), it))
                predicates.add(cb.exists(sub))
            }

            cb.and(*predicates.toTypedArray())
        }

    /**
     * 기준 위치에서 가까운 순 정렬. 같은 거리면 최신순이다.
     *
     * 거리는 저장된 값이 아니라 요청 좌표에 따라 매번 달라져 Sort 로 표현할 수 없으므로 조건과 함께 둔다.
     * 페이지 요청의 정렬은 비워 둬야 한다 — 정렬이 있으면 Spring Data 가 이 정렬을 덮어쓴다.
     * 건수 쿼리에서는 Spring Data 가 정렬을 지운다.
     *
     * @return 걸러 내는 조건 없이 정렬만 거는 조건
     */
    fun orderByDistanceFrom(origin: GeoPoint): Specification<TripRecordEntity> =
        Specification { root, query, cb ->
            // cb.literal 은 SQL 에 값을 그대로 박는다. 요청 좌표가 매번 달라 문장이 매번 달라지므로 바인딩한다.
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

    /**
     * 소속 여행 조인. 목록 쿼리에서는 여행과 소유자를 함께 읽는 fetch 조인이다.
     *
     * 건수 쿼리에서는 fetch 를 걸 수 없어(select 에 기록이 없다) 일반 조인으로 둔다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun joinTrip(root: Root<TripRecordEntity>, query: CriteriaQuery<*>): Join<TripRecordEntity, TripEntity> {
        if (query.resultType == Long::class.javaObjectType) return root.join("trip")
        val trip = root.fetch<TripRecordEntity, TripEntity>("trip")
        trip.fetch<TripEntity, UserEntity>("owner")
        return trip as Join<TripRecordEntity, TripEntity>
    }
}
