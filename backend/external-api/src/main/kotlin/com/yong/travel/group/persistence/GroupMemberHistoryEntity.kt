package com.yong.travel.group.persistence

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.group.domain.GroupMemberEvent
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
 * 멤버가 되거나 멤버가 아니게 된 기록. 한 번 쓰면 고치지 않는다 (append-only).
 *
 * 멤버 행(group_member)은 탈퇴·제외 즉시 지워 권한 판정에 끼어들지 않게 하고, 누가 언제부터
 * 언제까지 멤버였는지는 이 테이블이 답한다.
 */
@Entity
@Table(name = "group_member_history")
class GroupMemberHistoryEntity(
    /** 그룹이 지워져도 이력은 남아야 하므로 외래키를 걸지 않는다. */
    @Column(nullable = false)
    val groupId: Long,

    /** 삭제된 그룹도 이름을 답할 수 있게 사건 시점의 값을 복사해 둔다. */
    @Column(nullable = false, length = 30)
    val groupName: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val event: GroupMemberEvent,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val occurredAt: Instant = Instant.now()

    companion object {
        /**
         * 멤버십 사건을 이력 행으로 만든다.
         *
         * 기록 지점이 다섯이라(그룹 생성·초대 수락·탈퇴·제외·그룹 삭제) 만드는 방법을 한 곳에 모은다.
         *
         * @return 그룹 id·이름을 복사해 둔 이력 행. 아직 저장되지 않았다
         */
        fun of(group: GroupEntity, user: UserEntity, event: GroupMemberEvent) = GroupMemberHistoryEntity(
            groupId = requireNotNull(group.id),
            groupName = group.name,
            user = user,
            event = event,
        )
    }
}
