package com.yong.travel.auth.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 성공한 로그인 한 건. 한 번 쓰면 고치지 않으므로 필드를 모두 `val` 로 둔다.
 * 계정마다 로그인 수단이 하나라 수단 컬럼은 따로 두지 않는다.
 */
@Entity
@Table(name = "login_history")
class LoginHistoryEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(nullable = false, length = IP_ADDRESS_MAX_LENGTH)
    val ipAddress: String,

    @Column(length = USER_AGENT_MAX_LENGTH)
    val userAgent: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val loggedInAt: Instant = Instant.now()

    companion object {
        /** IPv6 표기의 최대 길이. */
        const val IP_ADDRESS_MAX_LENGTH = 45

        /** 넘는 값은 저장 전에 자른다. */
        const val USER_AGENT_MAX_LENGTH = 512
    }
}
