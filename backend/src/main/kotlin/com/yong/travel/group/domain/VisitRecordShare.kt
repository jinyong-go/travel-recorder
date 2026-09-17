package com.yong.travel.group.domain

import com.yong.travel.record.domain.VisitRecord
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
 * 기록 ↔ 그룹 공유 관계. `visibility = GROUP` 일 때만 의미가 있다.
 *
 * 공개 범위를 PRIVATE/PUBLIC 으로 바꿀 때 이 행들을 지운다. 남겨 두면 나중에 다시 GROUP 으로
 * 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
 */
@Entity
@Table(
    name = "visit_record_share",
    uniqueConstraints = [UniqueConstraint(columnNames = ["record_id", "group_id"])],
)
class VisitRecordShare(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    var record: VisitRecord,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: Group,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
