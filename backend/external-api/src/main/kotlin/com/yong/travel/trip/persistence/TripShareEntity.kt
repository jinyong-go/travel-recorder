package com.yong.travel.trip.persistence

import com.yong.travel.group.persistence.GroupEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 여행을 공유한 그룹. 공개 범위가 `GROUP` 일 때만 쓴다.
 * 다른 범위로 바꾸면 지운다. 남겨 두면 다시 `GROUP` 으로 바꿀 때 예전 공유가 되살아난다.
 */
@Entity
@Table(
    name = "trip_shares",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "group_id"])],
)
class TripShareEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: TripEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: GroupEntity,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
