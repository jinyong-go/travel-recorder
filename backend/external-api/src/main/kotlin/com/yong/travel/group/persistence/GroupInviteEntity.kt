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
 * 그룹 소유자가 사용자에게 보낸 대기 중인 초대.
 * 끝난 초대는 지우고 [InviteHistoryEntity] 로 옮기므로 상태 컬럼이 없다.
 */
@Entity
@Table(
    name = "group_invite",
    uniqueConstraints = [UniqueConstraint(columnNames = ["group_id", "invitee_id"])],
)
class GroupInviteEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: GroupEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    var invitee: UserEntity,

    /** 보낸 사람(그룹 소유자). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    var invitedBy: UserEntity,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
