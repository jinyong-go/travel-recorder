package com.yong.travel.photo.persistence

import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripVisibilityPredicates
import com.yong.travel.trip.record.persistence.TripRecordEntity
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

/** 사진 조회 조건. 사진을 볼 수 있는지는 소속 기록의 여행이 정한다. */
object PhotoSpecifications {

    /**
     * 요청자가 볼 수 있는 사진. 여행 공개 범위가 바뀌면 사진도 바로 막히도록 여행과 같은 판정을 쓴다.
     *
     * @param userId 비로그인이면 null 이며 전체 공개 여행의 사진만 해당한다
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
