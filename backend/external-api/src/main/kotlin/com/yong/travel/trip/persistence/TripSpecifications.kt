package com.yong.travel.trip.persistence

import com.yong.travel.common.util.LIKE_ESCAPE
import com.yong.travel.common.util.containsPattern
import com.yong.travel.trip.domain.TripScope
import org.springframework.data.jpa.domain.Specification

/** 여행 조회의 동적 조건. 목록과 단건 모두 공개 범위 판정이 여기서 쿼리에 실린다. */
object TripSpecifications {

    /**
     * id 일치 조건. 단건 조회를 [visibleTo]·[ownedBy] 와 묶어 판정까지 한 쿼리에 싣는다.
     *
     * @param tripId 여행 id
     * @return id 조건
     */
    fun withId(tripId: Long): Specification<TripEntity> =
        Specification { root, _, cb -> cb.equal(root.get<Long>("id"), tripId) }

    /**
     * 요청자가 볼 수 있는 여행. 단건 열람도 목록과 같은 판정식을 거친다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 전체 공개만 참이다
     * @return 열람 가능 조건
     */
    fun visibleTo(userId: Long?): Specification<TripEntity> =
        Specification { root, query, cb -> TripVisibilityPredicates.visibleTo(root, requireNotNull(query), cb, userId) }

    /**
     * 요청자가 소유한 여행. 기록을 담거나 옮길 대상 여행을 고를 때 쓴다.
     *
     * @param userId 요청자의 사용자 id
     * @return 소유 조건
     */
    fun ownedBy(userId: Long): Specification<TripEntity> =
        Specification { root, _, cb -> TripVisibilityPredicates.ownedBy(root, cb, userId) }

    /**
     * 조회 범위 조건.
     *
     * 공개 범위 판정을 조회 쿼리에 실어 보내는 것이 핵심이다. 전부 읽어 온 뒤 애플리케이션에서
     * 걸러 내면 페이지네이션 건수가 어긋나고, 거르는 걸 한 번 빠뜨리는 순간 곧바로 정보 유출이 된다.
     * 판정식 자체는 [TripVisibilityPredicates] 에 있다.
     *
     * @param scope 조회 범위
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며, 이 경우 PUBLIC 만 조회된다
     * @return 범위 조건
     */
    fun withScope(scope: TripScope, userId: Long?): Specification<TripEntity> =
        Specification { root, query, cb ->
            when (scope) {
                TripScope.MINE -> TripVisibilityPredicates.ownedBy(root, cb, userId)
                TripScope.PUBLIC -> TripVisibilityPredicates.isPublic(root, cb)
                // 내 여행은 MINE 에서 전부 보이므로 여기서 빼 중복 노출을 막는다.
                TripScope.SHARED -> cb.and(
                    TripVisibilityPredicates.sharedWith(root, requireNotNull(query), cb, userId),
                    cb.not(TripVisibilityPredicates.ownedBy(root, cb, userId)),
                )
            }
        }

    /**
     * 여행 이름 부분 일치 조건. 기록 목록과 달리 주소는 보지 않는다 — 여행은 주소를 갖지 않는다.
     *
     * @param keyword 검색어. 비었으면 조건을 걸지 않는다
     * @return 검색어 조건
     */
    fun withKeyword(keyword: String?): Specification<TripEntity> =
        Specification { root, _, cb ->
            val trimmed = keyword?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@Specification cb.conjunction()
            cb.like(root.get("name"), containsPattern(trimmed), LIKE_ESCAPE)
        }
}
