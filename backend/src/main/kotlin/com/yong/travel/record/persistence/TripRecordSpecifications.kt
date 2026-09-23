package com.yong.travel.record.persistence

import com.yong.travel.auth.persistence.User
import com.yong.travel.group.persistence.Group
import com.yong.travel.record.domain.Category
import com.yong.travel.record.persistence.TripRecord
import com.yong.travel.record.dto.RecordScope
import com.yong.travel.tag.persistence.Tag
import com.yong.travel.trip.persistence.Trip
import com.yong.travel.trip.persistence.TripShare
import com.yong.travel.trip.domain.Visibility
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery
import org.springframework.data.jpa.domain.Specification

object TripRecordSpecifications {

    /**
     * 조회 범위 조건.
     *
     * **판정 대상은 기록이 아니라 소속 여행이다** (명세 §4.4.1). 기록에는 공개 범위도 작성자도
     * 없으므로 여행을 조인하지 않으면 판정할 근거가 없다.
     *
     * 공개 범위 판정을 조회 쿼리에 실어 보내는 것이 핵심이다. 전부 읽어 온 뒤 애플리케이션에서
     * 걸러 내면 페이지네이션 건수가 어긋나고, 거르는 걸 한 번 빠뜨리는 순간 곧바로 정보 유출이 된다.
     *
     * 조인 자체가 삭제 필터 역할도 한다 — Trip 의 @SQLRestriction 덕에 삭제된 여행의 기록은
     * 조인 단계에서 함께 사라진다.
     *
     * @param userId 비로그인이면 null. 이 경우 PUBLIC 만 조회된다.
     * @param groupIds 요청자가 속한 그룹 id 목록
     */
    fun withScope(scope: RecordScope, userId: Long?, groupIds: List<Long>): Specification<TripRecord> =
        Specification { root, query, cb ->
            val trip = root.join<TripRecord, Trip>("trip")
            when (scope) {
                RecordScope.MINE ->
                    if (userId == null) cb.disjunction() else cb.equal(ownerId(trip), userId)

                RecordScope.PUBLIC ->
                    cb.equal(trip.get<Visibility>("visibility"), Visibility.PUBLIC)

                RecordScope.SHARED -> {
                    if (userId == null || groupIds.isEmpty()) {
                        cb.disjunction()
                    } else {
                        // 내 여행의 기록은 MINE 에서 전부 보이므로 여기서 빼 중복 노출을 막는다.
                        cb.and(
                            cb.equal(trip.get<Visibility>("visibility"), Visibility.GROUP),
                            cb.notEqual(ownerId(trip), userId),
                            cb.exists(sharedWithGroups(trip, query, cb, groupIds)),
                        )
                    }
                }
            }
        }

    /**
     * 목록 필터.
     *
     * `tripId` 는 여행 상세 화면이 하위 기록을 가져올 때 쓴다. 범위 조건과 AND 로 묶이므로,
     * 볼 수 없는 여행의 id 를 넣어도 결과가 새지 않고 빈 목록이 된다 (명세 §4.4.1).
     */
    fun withFilters(
        tripId: Long?,
        category: Category?,
        tag: String?,
        keyword: String?,
    ): Specification<TripRecord> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            tripId?.let { predicates.add(cb.equal(root.get<Trip>("trip").get<Long>("id"), it)) }

            category?.let { predicates.add(cb.equal(root.get<Category>("category"), it)) }

            keyword?.takeIf { it.isNotBlank() }?.let {
                val like = "%${it.trim()}%"
                predicates.add(
                    cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("address"), like),
                    ),
                )
            }

            tag?.takeIf { it.isNotBlank() }?.let {
                query?.distinct(true)
                val tagJoin = root.join<TripRecord, Tag>("tags")
                predicates.add(cb.equal(tagJoin.get<String>("name"), it.trim()))
            }

            cb.and(*predicates.toTypedArray())
        }

    private fun ownerId(trip: Join<TripRecord, Trip>) =
        trip.get<User>("owner").get<Long>("id")

    private fun sharedWithGroups(
        trip: Join<TripRecord, Trip>,
        query: CriteriaQuery<*>?,
        cb: CriteriaBuilder,
        groupIds: List<Long>,
    ): Subquery<Long> {
        val sub = requireNotNull(query).subquery(Long::class.java)
        val share = sub.from(TripShare::class.java)
        sub.select(cb.literal(1L))
        sub.where(
            cb.equal(share.get<Trip>("trip"), trip),
            share.get<Group>("group").get<Long>("id").`in`(groupIds),
        )
        return sub
    }
}
