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
import java.time.Instant

/**
 * 여행을 공유할 사람들의 목록. 멤버는 이 그룹에 공유된 여행만 볼 수 있고 편집은 할 수 없다.
 * `group` 은 SQL 예약어라 테이블명을 `share_group` 으로 둔다.
 */
@Entity
@Table(name = "share_group")
class GroupEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: UserEntity,

    @Column(nullable = false, length = 30)
    var name: String,

    /** 그룹 설명. 멤버 모두에게 보인다. */
    @Column(length = 200)
    var memo: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    companion object {
        /** 소유자를 포함한 그룹당 최대 인원. */
        const val MEMBER_LIMIT = 5

        /**
         * 사용자당 소유·참여 그룹 수 상한.
         * 하나로 합치면 그룹을 많이 만든 사람이 초대를 수락하지 못하게 되어 나눴다.
         */
        const val OWNED_GROUP_LIMIT = 50
        const val JOINED_GROUP_LIMIT = 50
    }
}
