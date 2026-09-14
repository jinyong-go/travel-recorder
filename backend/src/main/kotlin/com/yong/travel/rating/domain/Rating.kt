package com.yong.travel.rating.domain

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
import java.time.Instant

@Entity
@Table(
    name = "ratings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["place_id", "user_id"])],
)
class Rating(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    var place: Place,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var score: Int,
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

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
