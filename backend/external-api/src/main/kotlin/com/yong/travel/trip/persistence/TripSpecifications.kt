package com.yong.travel.trip.persistence

import com.yong.travel.common.util.LIKE_ESCAPE
import com.yong.travel.common.util.containsPattern
import com.yong.travel.common.util.trimToNull
import com.yong.travel.trip.domain.TripScope
import org.springframework.data.jpa.domain.Specification

/** 여행 조회 조건. */
object TripSpecifications {

    /** id 일치. */
    fun withId(tripId: Long): Specification<TripEntity> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("id"), tripId) }

    /**
     * 요청자가 볼 수 있는 여행.
     *
     * @param userId 비로그인이면 null 이며 전체 공개만 해당한다
     */
    fun visibleTo(userId: Long?): Specification<TripEntity> =
        Specification { root, query, cb -> TripVisibilityPredicates.visibleTo(root, requireNotNull(query), cb, userId) }

    /** 요청자가 소유한 여행. */
    fun ownedBy(userId: Long): Specification<TripEntity> =
        Specification { root, _, cb -> TripVisibilityPredicates.ownedBy(root, cb, userId) }

    /**
     * 조회 범위 조건.
     *
     * @param userId 비로그인이면 null 이며 PUBLIC 만 조회된다
     */
    fun withScope(scope: TripScope, userId: Long?): Specification<TripEntity> =
        Specification { root, query, cb ->
            when (scope) {
                TripScope.MINE -> TripVisibilityPredicates.ownedBy(root, cb, userId)
                TripScope.PUBLIC -> TripVisibilityPredicates.isPublic(root, cb)
                TripScope.SHARED -> TripVisibilityPredicates.sharedByOthersWith(root, requireNotNull(query), cb, userId)
            }
        }

    /**
     * 여행 이름 부분 일치.
     *
     * @param keyword 비었으면 조건을 걸지 않는다
     */
    fun withKeyword(keyword: String?): Specification<TripEntity> =
        Specification { root, _, cb ->
            val trimmed = keyword.trimToNull()
                ?: return@Specification cb.conjunction()
            cb.like(root.get("name"), containsPattern(trimmed), LIKE_ESCAPE)
        }
}
