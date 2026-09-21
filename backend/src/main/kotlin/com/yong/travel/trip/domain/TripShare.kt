package com.yong.travel.trip.domain

import com.yong.travel.group.domain.Group
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
 * 여행 ↔ 그룹 공유 관계. `visibility = GROUP` 일 때만 의미가 있다.
 *
 * 공개 범위를 PRIVATE/PUBLIC 으로 바꿀 때 이 행들을 지운다. 남겨 두면 나중에 다시 GROUP 으로
 * 되돌렸을 때 예전 공유가 의도치 않게 되살아난다 (명세 §3.1).
 *
 * 기록 단위의 공유 관계는 존재하지 않는다. 공유는 여행에서 한 번만 정해진다.
 */
@Entity
@Table(
    name = "trip_shares",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "group_id"])],
)
class TripShare(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: Group,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
