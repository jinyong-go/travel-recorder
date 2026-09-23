package com.yong.travel.group.persistence

import com.yong.travel.auth.persistence.User
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
 * 끝난 초대의 기록. 한 번 쓰면 고치지 않는다 (append-only).
 *
 * 대기 중인 초대에 상태 컬럼을 달지 않고 별도 테이블로 옮기는 이유는
 * `unique(group_id, invitee_id)` 때문이다 — 끝난 초대가 그 자리에 남아 있으면 같은 상대를
 * 다시 초대할 수 없다 (명세 §3.2).
 *
 * 다른 엔티티와 달리 필드를 `val` 로 둔다. 수정 진입점을 만들지 않는 것이 이 테이블의 요점이다.
 */
@Entity
@Table(name = "invite_history")
class InviteHistory(
    /** 그룹이 지워져도 이력은 남아야 하므로 외래키를 걸지 않는다 (명세 §3.1). */
    @Column(nullable = false)
    val groupId: Long,

    /** 삭제된 그룹도 이름을 답할 수 있게 끝난 시점의 값을 복사해 둔다 (공통 명세 §3.7, §3.8). */
    @Column(nullable = false, length = 30)
    val groupName: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    val invitee: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    val invitedBy: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val outcome: InviteOutcome,

    /** 원래 초대의 생성 시각. 언제 보낸 것이 언제 끝났는지 이력만으로 답할 수 있어야 한다. */
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
        /**
         * 끝난 초대를 이력으로 옮긴다.
         *
         * 기록 지점이 넷이라(수락·거절·취소·그룹 삭제, 명세 §3.1) 만드는 방법을 한 곳에 모은다.
         * 한 곳만 빠뜨려도 이력이 조용히 비고, 그런 누락은 조회 시점에 드러나지 않는다.
         */
        fun from(invite: GroupInvite, outcome: InviteOutcome) = InviteHistory(
            groupId = requireNotNull(invite.group.id),
            groupName = invite.group.name,
            invitee = invite.invitee,
            invitedBy = invite.invitedBy,
            outcome = outcome,
            invitedAt = invite.createdAt,
        )
    }
}
