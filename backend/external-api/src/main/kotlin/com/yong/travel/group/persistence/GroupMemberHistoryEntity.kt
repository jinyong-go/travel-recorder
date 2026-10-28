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

/** 멤버십이 시작되거나 끝난 기록. 한 번 쓰면 고치지 않는다. */
@Entity
@Table(name = "group_member_history")
class GroupMemberHistoryEntity(
    /** 그룹이 지워져도 이력은 남아야 하므로 외래키를 걸지 않는다. */
    @Column(nullable = false)
    val groupId: Long,

    /** 그룹이 지워져도 남도록 이름을 복사해 둔다. */
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
        /** 멤버십 사건으로 이력 행을 만든다. 저장은 호출부가 한다. */
        fun of(group: GroupEntity, user: UserEntity, event: GroupMemberEvent) = GroupMemberHistoryEntity(
            groupId = requireNotNull(group.id),
            groupName = group.name,
            user = user,
            event = event,
        )
    }
}
