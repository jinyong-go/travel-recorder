package com.yong.travel.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 동일인 판정은 `provider + providerId` 로 한다. 이메일은 초대 대상을 지정하는 유일한 열쇠라
 * (공통 명세 §3.7) 계정마다 하나여야 하므로 유니크 제약을 함께 건다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["provider", "provider_id"]),
        UniqueConstraint(columnNames = ["email"]),
    ],
)
class User(
    @Column(nullable = false)
    var provider: String,

    @Column(name = "provider_id", nullable = false)
    var providerId: String,

    /**
     * 네이버가 이메일 제공 동의를 받지 못하면 내려주지 않으므로 null 을 허용한다.
     * 빈 문자열로 채우면 그런 계정이 둘째로 생기는 순간 유니크 제약에 걸려 로그인 자체가 실패한다.
     * null 끼리는 충돌하지 않는다. 이메일이 없는 계정은 초대 대상으로 지정할 수 없다.
     */
    var email: String? = null,

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
