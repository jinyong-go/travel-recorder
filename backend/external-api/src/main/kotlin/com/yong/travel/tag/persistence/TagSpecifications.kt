package com.yong.travel.tag.persistence

import com.yong.travel.common.util.LIKE_ESCAPE
import com.yong.travel.common.util.containsPattern
import com.yong.travel.common.util.trimToNull
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripVisibilityPredicates
import com.yong.travel.trip.record.persistence.TripRecordEntity
import org.springframework.data.jpa.domain.Specification

/** 태그 조회 조건. */
object TagSpecifications {

    /**
     * 요청자가 볼 수 있는 기록에 쓰인 태그. 비공개 기록의 태그가 드러나지 않도록 제한한다.
     *
     * @param userId 비로그인이면 null 이며 전체 공개 여행의 기록만 본다
     */
    fun usedInRecordVisibleTo(userId: Long?): Specification<TagEntity> =
        Specification { tag, query, cb ->
            val sub = requireNotNull(query).subquery(Long::class.java)
            val record = sub.from(TripRecordEntity::class.java)
            val trip = record.join<TripRecordEntity, TripEntity>("trip")
            sub.select(cb.literal(1L)).where(
                cb.equal(record.join<TripRecordEntity, TagEntity>("tags"), tag),
                TripVisibilityPredicates.visibleTo(trip, sub, cb, userId),
            )
            cb.exists(sub)
        }

    /**
     * 이름 부분 일치. 대소문자는 가리지 않는다.
     *
     * @param keyword 비었으면 조건을 걸지 않는다
     */
    fun nameContains(keyword: String?): Specification<TagEntity> =
        Specification { tag, _, cb ->
            val trimmed = keyword.trimToNull()
                ?: return@Specification cb.conjunction()
            cb.like(cb.lower(tag.get("name")), containsPattern(trimmed.lowercase()), LIKE_ESCAPE)
        }
}
