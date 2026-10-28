package com.yong.travel.group.persistence

import com.yong.travel.auth.persistence.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 그룹 멤버. 소유자도 멤버로 둔다.
 * 탈퇴·제외 즉시 권한이 사라지도록 soft delete 하지 않고 바로 지운다.
 */
@Entity
@Table(
    name = "group_member",
    uniqueConstraints = [UniqueConstraint(columnNames = ["group_id", "user_id"])],
)
class GroupMemberEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: GroupEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var joinedAt: Instant = Instant.now()
        protected set
}
