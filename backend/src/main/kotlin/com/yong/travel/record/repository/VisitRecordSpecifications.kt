package com.yong.travel.record.repository

import com.yong.travel.auth.domain.User
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.VisitRecordShare
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.VisitRecord
import com.yong.travel.record.domain.Visibility
import com.yong.travel.record.dto.RecordScope
import com.yong.travel.tag.domain.Tag
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object VisitRecordSpecifications {

    /**
     * 조회 범위 조건.
     *
     * 공개 범위 판정을 조회 쿼리에 실어 보내는 것이 핵심이다. 전부 읽어 온 뒤 애플리케이션에서
     * 걸러 내면 페이지네이션 건수가 어긋나고, 거르는 걸 한 번 빠뜨리는 순간 곧바로 정보 유출이 된다.
     *
     * @param userId 비로그인이면 null. 이 경우 PUBLIC 만 조회된다.
     * @param groupIds 요청자가 속한 그룹 id 목록
     */
    fun withScope(scope: RecordScope, userId: Long?, groupIds: List<Long>): Specification<VisitRecord> =
        Specification { root, query, cb ->
            when (scope) {
                RecordScope.MINE ->
                    if (userId == null) cb.disjunction() else cb.equal(authorId(root), userId)

                RecordScope.PUBLIC ->
                    cb.equal(root.get<Visibility>("visibility"), Visibility.PUBLIC)

                RecordScope.SHARED -> {
                    if (userId == null || groupIds.isEmpty()) {
                        cb.disjunction()
                    } else {
                        // 내 기록은 MINE 에서 전부 보이므로 여기서 빼 중복 노출을 막는다.
                        cb.and(
                            cb.equal(root.get<Visibility>("visibility"), Visibility.GROUP),
                            cb.notEqual(authorId(root), userId),
                            cb.exists(sharedWithGroups(root, query, cb, groupIds)),
                        )
                    }
                }
            }
        }

    fun withFilters(category: Category?, tag: String?, keyword: String?): Specification<VisitRecord> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

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
                val tagJoin = root.join<VisitRecord, Tag>("tags")
                predicates.add(cb.equal(tagJoin.get<String>("name"), it.trim()))
            }

            cb.and(*predicates.toTypedArray())
        }

    private fun authorId(root: jakarta.persistence.criteria.Root<VisitRecord>) =
        root.get<User>("author").get<Long>("id")

    private fun sharedWithGroups(
        root: jakarta.persistence.criteria.Root<VisitRecord>,
        query: jakarta.persistence.criteria.CriteriaQuery<*>?,
        cb: jakarta.persistence.criteria.CriteriaBuilder,
        groupIds: List<Long>,
    ): jakarta.persistence.criteria.Subquery<Long> {
        val sub = requireNotNull(query).subquery(Long::class.java)
        val share = sub.from(VisitRecordShare::class.java)
        sub.select(cb.literal(1L))
        sub.where(
            cb.equal(share.get<VisitRecord>("record"), root),
            share.get<Group>("group").get<Long>("id").`in`(groupIds),
        )
        return sub
    }
}
