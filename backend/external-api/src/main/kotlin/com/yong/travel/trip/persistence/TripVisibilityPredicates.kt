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
 * 여행 공개 범위 판정을 조회 조건으로 만든다. 여행·기록의 목록과 단건 조회, 태그 자동완성이 함께 쓴다.
 *
 * 판정식이 조회마다 따로 있으면 한쪽만 고쳐졌을 때 그 경로로 정보가 샌다. 메모리 판정은 두지 않고
 * 모든 조회 경로가 이 함수들을 거친다. 읽어 온 뒤 걸러 내면 페이지네이션 건수도 어긋난다.
 *
 * 공유 판정은 요청자가 속한 그룹 id 를 미리 읽어 넘기지 않고 group_member 를 서브쿼리로 붙인다.
 * 조회 한 번으로 끝나고, 미리 읽은 목록과 판정 시점 사이에 탈퇴가 끼어들 틈도 없다.
 */
object TripVisibilityPredicates {

    /**
     * 요청자가 소유한 여행.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 항상 거짓이다
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
     * 요청자가 속한 그룹 중 하나 이상에 공유된 GROUP 여행.
     *
     * 소유자 본인 여부는 따지지 않는다 — 목록의 SHARED 범위는 [sharedByOthersWith] 가 본인 것을 뺀다.
     *
     * @param query 서브쿼리를 붙일 쿼리. 바깥 쿼리든 서브쿼리든 된다
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 항상 거짓이다
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
     * 요청자가 속한 그룹에 공유된 **남의** 여행. 여행 목록과 기록 목록의 SHARED 범위다.
     *
     * 내 여행은 MINE 범위에서 전부 보이므로 여기서 빼 중복 노출을 막는다. 두 목록이 이 함수 하나를
     * 써야 한쪽만 고쳐져 노출이 갈라지는 일이 없다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 항상 거짓이다
     */
    fun sharedByOthersWith(
        trip: Path<TripEntity>,
        query: CommonAbstractCriteria,
        cb: CriteriaBuilder,
        userId: Long?,
    ): Predicate =
        cb.and(sharedWith(trip, query, cb, userId), cb.not(ownedBy(trip, cb, userId)))

    /**
     * 요청자가 볼 수 있는 여행 전부 — 내 여행이거나, 전체 공개이거나, 내 그룹에 공유됐거나 셋 중 하나다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 전체 공개만 참이다
     */
    fun visibleTo(
        trip: Path<TripEntity>,
        query: CommonAbstractCriteria,
        cb: CriteriaBuilder,
        userId: Long?,
    ): Predicate =
        cb.or(ownedBy(trip, cb, userId), isPublic(trip, cb), sharedWith(trip, query, cb, userId))
}
