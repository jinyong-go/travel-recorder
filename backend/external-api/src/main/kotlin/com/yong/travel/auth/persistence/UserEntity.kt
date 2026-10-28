package com.yong.travel.auth.persistence

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 사용자. 로그인 수단과 수단 안의 id 로 동일인을 가린다.
 * 이메일은 초대 대상을 찾는 열쇠라 중복을 막는다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["provider", "provider_id"]),
        UniqueConstraint(columnNames = ["email"]),
    ],
)
class UserEntity(
    @Column(nullable = false)
    var provider: String,

    @Column(name = "provider_id", nullable = false)
    var providerId: String,

    /**
     * 이메일 제공에 동의하지 않으면 null 이다. 빈 문자열로 채우면 그런 계정끼리 유니크 제약에 걸린다.
     * 이메일이 없는 계정은 초대할 수 없다.
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

/** 이메일을 포함한 본인 정보로 바꾼다. */
fun UserEntity.toProfile(): MyProfile = MyProfile(requireNotNull(id), name, email, profileImageUrl)

/** 타인에게 보이는 사용자 정보로 바꾼다. 이메일은 담지 않는다. */
fun UserEntity.toUser(): User = User(requireNotNull(id), name, profileImageUrl)
