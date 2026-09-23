package com.yong.travel.group.persistence

import com.yong.travel.auth.persistence.User
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
 * 그룹 소유자가 특정 사용자 앞으로 보낸 가입 요청. 받은 사람의 계정에 쌓인다.
 *
 * 토큰·만료 컬럼을 두지 않는다 — 초대가 서비스 밖으로 나가지 않으므로 추측할 대상도, 수명도 없다.
 * 상태 컬럼도 두지 않는다 — 수락·거절·철회가 모두 행 삭제로 끝나고, 거절 이력을 남기지 않는 것이
 * 의도된 동작이다 (공통 명세 §3.7, §3.9).
 */
@Entity
@Table(
    name = "group_invite",
    uniqueConstraints = [UniqueConstraint(columnNames = ["group_id", "invitee_id"])],
)
class GroupInvite(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: Group,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    var invitee: User,

    /** 보낸 사람 = 초대 시점의 그룹 소유자. 소유자는 바뀌지 않지만 받은 사람에게 보여 줄 이름이라 따로 갖는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    var invitedBy: User,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
