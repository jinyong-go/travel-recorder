package com.yong.travel.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])],
)
class User(
    @Column(nullable = false)
    var provider: String,

    @Column(name = "provider_id", nullable = false)
    var providerId: String,

    @Column(nullable = false)
    var email: String,

    @Column(nullable = false)
    var name: String,

    var profileImageUrl: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
