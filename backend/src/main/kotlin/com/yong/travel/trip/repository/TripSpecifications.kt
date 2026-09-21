package com.yong.travel.trip.repository

import com.yong.travel.auth.domain.User
import com.yong.travel.group.domain.Group
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripShare
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.dto.TripScope
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Subquery
import org.springframework.data.jpa.domain.Specification

object TripSpecifications {

    /**
     * 조회 범위 조건 (명세 §4.1).
     *
     * 공개 범위 판정을 조회 쿼리에 실어 보내는 것이 핵심이다. 전부 읽어 온 뒤 애플리케이션에서
     * 걸러 내면 페이지네이션 건수가 어긋나고, 거르는 걸 한 번 빠뜨리는 순간 곧바로 정보 유출이 된다.
     *
     * @param userId 비로그인이면 null. 이 경우 PUBLIC 만 조회된다.
     * @param groupIds 요청자가 속한 그룹 id 목록
     */
    fun withScope(scope: TripScope, userId: Long?, groupIds: List<Long>): Specification<Trip> =
        Specification { root, query, cb ->
            when (scope) {
                TripScope.MINE ->
                    if (userId == null) cb.disjunction() else cb.equal(ownerId(root), userId)

                TripScope.PUBLIC ->
                    cb.equal(root.get<Visibility>("visibility"), Visibility.PUBLIC)

                TripScope.SHARED -> {
                    if (userId == null || groupIds.isEmpty()) {
                        cb.disjunction()
                    } else {
                        // 내 여행은 MINE 에서 전부 보이므로 여기서 빼 중복 노출을 막는다.
                        cb.and(
                            cb.equal(root.get<Visibility>("visibility"), Visibility.GROUP),
                            cb.notEqual(ownerId(root), userId),
                            cb.exists(sharedWithGroups(root, query, cb, groupIds)),
                        )
                    }
                }
            }
        }

    /** 여행 이름 부분 일치. 기록 목록과 달리 주소는 보지 않는다 — 여행은 주소를 갖지 않는다. */
    fun withKeyword(keyword: String?): Specification<Trip> =
        Specification { root, _, cb ->
            val trimmed = keyword?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@Specification cb.conjunction()
            cb.like(root.get("name"), "%$trimmed%")
        }

    private fun ownerId(root: Root<Trip>) = root.get<User>("owner").get<Long>("id")

    private fun sharedWithGroups(
        root: Root<Trip>,
        query: CriteriaQuery<*>?,
        cb: CriteriaBuilder,
        groupIds: List<Long>,
    ): Subquery<Long> {
        val sub = requireNotNull(query).subquery(Long::class.java)
        val share = sub.from(TripShare::class.java)
        sub.select(cb.literal(1L))
        sub.where(
            cb.equal(share.get<Trip>("trip"), root),
            share.get<Group>("group").get<Long>("id").`in`(groupIds),
        )
        return sub
    }
}
