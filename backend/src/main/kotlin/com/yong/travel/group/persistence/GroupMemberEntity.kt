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
 * 그룹 멤버. 소유자도 멤버 행을 가진다 (인원 계산과 조회 권한 판정을 한 곳에서 처리하기 위해서다).
 *
 * soft delete 대상이 아니다. 탈퇴·제외는 즉시 조회 권한을 없애야 하는 동작이라,
 * 남아 있는 행이 권한 판정에 끼어들 여지를 만들지 않는다.
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
