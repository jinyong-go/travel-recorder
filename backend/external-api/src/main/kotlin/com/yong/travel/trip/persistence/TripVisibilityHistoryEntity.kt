package com.yong.travel.trip.persistence

import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.domain.TripVisibilityChangeCause
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 여행 공개 범위가 바뀐 뒤의 상태 기록. 한 번 쓰면 고치지 않는다 (append-only).
 *
 * 권한 판정은 이 테이블을 읽지 않는다. 판정은 trips·trip_shares 의 현재 값으로만 한다.
 *
 * 다른 엔티티와 달리 필드를 `val` 로 둔다. 수정 진입점을 만들지 않는 것이 이 테이블의 요점이다.
 */
@Entity
@Table(name = "trip_visibility_history")
class TripVisibilityHistoryEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: TripEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val visibility: TripVisibility,

    /** 그룹이 지워져도 남아야 하므로 외래키 없이 id 만 담는다. 같은 상태가 같은 값이 되도록 오름차순이다. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    val groupIds: List<Long>,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val cause: TripVisibilityChangeCause,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val changedAt: Instant = Instant.now()
}
