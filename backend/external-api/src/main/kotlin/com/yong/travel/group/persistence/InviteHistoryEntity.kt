package com.yong.travel.group.persistence

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.group.domain.InviteOutcome
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 끝난 초대의 기록. 한 번 쓰면 고치지 않는다.
 * 끝난 초대가 대기 초대 자리에 남으면 같은 사람을 다시 초대할 수 없어 별도 테이블로 옮긴다.
 */
@Entity
@Table(name = "invite_history")
class InviteHistoryEntity(
    /** 그룹이 지워져도 이력은 남아야 하므로 외래키를 걸지 않는다. */
    @Column(nullable = false)
    val groupId: Long,

    /** 그룹이 지워져도 남도록 이름을 복사해 둔다. */
    @Column(nullable = false, length = 30)
    val groupName: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    val invitee: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    val invitedBy: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val outcome: InviteOutcome,

    /** 초대를 보낸 시각. */
    @Column(nullable = false)
    val invitedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val resolvedAt: Instant = Instant.now()

    companion object {
        /** 끝난 초대로 이력 행을 만든다. 저장은 호출부가 한다. */
        fun from(invite: GroupInviteEntity, outcome: InviteOutcome) = InviteHistoryEntity(
            groupId = requireNotNull(invite.group.id),
            groupName = invite.group.name,
            invitee = invite.invitee,
            invitedBy = invite.invitedBy,
            outcome = outcome,
            invitedAt = invite.createdAt,
        )
    }
}
