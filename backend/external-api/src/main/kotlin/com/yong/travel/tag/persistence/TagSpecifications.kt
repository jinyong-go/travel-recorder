package com.yong.travel.tag.persistence

import com.yong.travel.common.util.LIKE_ESCAPE
import com.yong.travel.common.util.containsPattern
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripVisibilityPredicates
import com.yong.travel.trip.record.persistence.TripRecordEntity
import org.springframework.data.jpa.domain.Specification

/** 태그 자동완성의 조회 조건. */
object TagSpecifications {

    /**
     * 요청자가 볼 수 있는 기록에 한 번이라도 쓰인 태그.
     * 전체 태그를 그대로 내려주면 비공개 기록에만 쓰인 태그가 그대로 드러난다.
     *
     * 태그에서 출발해 EXISTS 로 확인한다. 기록에서 출발하면 볼 수 있는 기록 전부를 태그와 묶은 뒤에야
     * 중복을 지우고 자를 수 있지만, 이 방향은 이름순으로 읽다가 상한만큼 찾으면 멈출 수 있다.
     *
     * **판정 대상은 기록이 아니라 소속 여행이다** — 공개 범위가 여행에만 있으므로 여행을 조인하지
     * 않으면 판정할 근거가 없고, 조인 없는 조회 경로는 그대로 우회로가 된다. 지워진 기록·여행은
     * 두 엔티티의 @SQLRestriction 이 서브쿼리에서도 걸러 낸다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 전체 공개 여행의 기록만 본다
     * @return 열람 가능한 기록에 쓰인 태그 조건
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
     * 이름의 대소문자 무시 부분 일치.
     *
     * @param keyword 검색어. 비었으면 조건을 걸지 않는다
     * @return 이름 조건
     */
    fun nameContains(keyword: String?): Specification<TagEntity> =
        Specification { tag, _, cb ->
            val trimmed = keyword?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@Specification cb.conjunction()
            cb.like(cb.lower(tag.get("name")), containsPattern(trimmed.lowercase()), LIKE_ESCAPE)
        }
}
