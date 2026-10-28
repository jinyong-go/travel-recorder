package com.yong.travel.trip.persistence

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.persistence.GroupMemberEntity
import com.yong.travel.trip.domain.TripVisibility
import jakarta.persistence.criteria.CommonAbstractCriteria
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate

/**
 * 여행 공개 범위 판정을 조회 조건으로 만든다.
 * 판정이 여러 곳에 있으면 한쪽만 고쳐졌을 때 정보가 새므로 모든 조회가 이것을 쓴다.
 */
object TripVisibilityPredicates {

    /**
     * 요청자가 소유한 여행.
     *
     * @param userId 비로그인이면 null 이며 항상 거짓이다
     */
    fun ownedBy(trip: Path<TripEntity>, cb: CriteriaBuilder, userId: Long?): Predicate =
        if (userId == null) {
            cb.disjunction()
        } else {
            cb.equal(trip.get<UserEntity>("owner").get<Long>("id"), userId)
        }

    /** 전체 공개 여행. */
    fun isPublic(trip: Path<TripEntity>, cb: CriteriaBuilder): Predicate =
        cb.equal(trip.get<TripVisibility>("visibility"), TripVisibility.PUBLIC)

    /**
     * 요청자가 속한 그룹에 공유된 여행. 소유자인지는 따지지 않는다.
     *
     * @param userId 비로그인이면 null 이며 항상 거짓이다
     */
    fun sharedWith(
        trip: Path<TripEntity>,
        query: CommonAbstractCriteria,
        cb: CriteriaBuilder,
        userId: Long?,
    ): Predicate {
        if (userId == null) return cb.disjunction()
        val sub = query.subquery(Long::class.java)
        val share = sub.from(TripShareEntity::class.java)
        val member = sub.from(GroupMemberEntity::class.java)
        sub.select(cb.literal(1L)).where(
            cb.equal(share.get<TripEntity>("trip"), trip),
            cb.equal(member.get<GroupEntity>("group"), share.get<GroupEntity>("group")),
            cb.equal(member.get<UserEntity>("user").get<Long>("id"), userId),
        )
        return cb.and(cb.equal(trip.get<TripVisibility>("visibility"), TripVisibility.GROUP), cb.exists(sub))
    }

    /**
     * 요청자가 속한 그룹에 공유된 남의 여행. 내 여행은 MINE 범위에서 보이므로 뺀다.
     *
     * @param userId 비로그인이면 null 이며 항상 거짓이다
     */
    fun sharedByOthersWith(
        trip: Path<TripEntity>,
        query: CommonAbstractCriteria,
        cb: CriteriaBuilder,
        userId: Long?,
    ): Predicate =
        cb.and(sharedWith(trip, query, cb, userId), cb.not(ownedBy(trip, cb, userId)))

    /**
     * 요청자가 볼 수 있는 여행. 내 여행, 전체 공개 여행, 내 그룹에 공유된 여행이다.
     *
     * @param userId 비로그인이면 null 이며 전체 공개만 참이다
     */
    fun visibleTo(
        trip: Path<TripEntity>,
        query: CommonAbstractCriteria,
        cb: CriteriaBuilder,
        userId: Long?,
    ): Predicate =
        cb.or(ownedBy(trip, cb, userId), isPublic(trip, cb), sharedWith(trip, query, cb, userId))
}
