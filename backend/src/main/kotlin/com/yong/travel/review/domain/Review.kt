package com.yong.travel.review.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.place.domain.Place
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * 여행지에 대한 사용자 1인의 리뷰 = 별점 + 코멘트.
 * 별점과 코멘트를 따로 남길 수 없으며, `(place_id, user_id)` 유니크 제약으로 사용자당 1건만 존재한다.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = [UniqueConstraint(columnNames = ["place_id", "user_id"])],
)
@SQLRestriction("deleted_at is null")
class Review(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    var place: Place,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    /** 0.5 ~ 5.0, 0.5 단위. 0.5 단위 값은 이진 부동소수점으로 정확히 표현되므로 오차 없이 비교·집계된다. */
    @Column(nullable = false)
    var score: Double,

    /** 별점과 함께 남기는 코멘트. 별점만 남기는 것도 허용하므로 필수는 아니다. */
    @Column(length = 1000)
    var content: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    /** soft delete 시각. null 이면 살아 있는 행이다 (@SQLRestriction 으로 조회에서 자동 제외). */
    var deletedAt: Instant? = null
        protected set

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }

    /**
     * (place_id, user_id) 유니크 제약 때문에 삭제 후 재등록 시 새 행을 넣을 수 없다.
     * 그래서 soft delete 된 행을 그대로 되살려 쓴다.
     */
    fun restore() {
        deletedAt = null
    }
}
