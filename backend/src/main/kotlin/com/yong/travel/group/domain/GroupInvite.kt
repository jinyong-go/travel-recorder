package com.yong.travel.group.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.Instant

/**
 * 그룹 초대 링크.
 *
 * 그룹당 1건만 존재하며(`group_id` 유니크), 재발급은 새 행을 만들지 않고 이 행의 토큰과 만료 시각을
 * 갈아 끼운다. 그래서 이전 링크는 재발급과 동시에 무효가 된다 — 메신저 대화방에 남은 오래된 링크가
 * 계속 살아 있는 상태를 막기 위해서다.
 */
@Entity
@Table(
    name = "group_invite",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["group_id"]),
        UniqueConstraint(columnNames = ["token"]),
    ],
)
class GroupInvite(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: Group,

    @Column(nullable = false)
    var token: String,

    @Column(nullable = false)
    var expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    fun reissue(token: String, expiresAt: Instant) {
        this.token = token
        this.expiresAt = expiresAt
    }

    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt.isBefore(now)

    companion object {
        val TTL: Duration = Duration.ofDays(7)
    }
}
