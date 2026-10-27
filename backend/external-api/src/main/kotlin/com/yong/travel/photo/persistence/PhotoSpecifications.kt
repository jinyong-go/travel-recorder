package com.yong.travel.photo.persistence

import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripVisibilityPredicates
import com.yong.travel.trip.record.persistence.TripRecordEntity
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

/** 사진 조회 조건. 사진을 볼 수 있는지는 소속 기록의 여행이 정한다. */
object PhotoSpecifications {

    /**
     * 요청자가 볼 수 있는 사진 — 소속 기록의 여행을 볼 수 있는 사진이다. 여행·기록과 같은 판정식을 거쳐야
     * 범위를 좁히거나 그룹에서 나간 순간 사진도 함께 막힌다.
     *
     * 지워진 기록·여행의 사진은 조인에 붙는 @SQLRestriction 이 걸러 낸다.
     *
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 전체 공개 여행의 사진만 참이다
     */
    fun viewableBy(photoId: UUID, userId: Long?): Specification<PhotoEntity> =
        Specification { photo, query, cb ->
            val trip = photo.join<PhotoEntity, TripRecordEntity>("record").join<TripRecordEntity, TripEntity>("trip")
            cb.and(
                cb.equal(photo.get<UUID>("id"), photoId),
                TripVisibilityPredicates.visibleTo(trip, requireNotNull(query), cb, userId),
            )
        }
}
