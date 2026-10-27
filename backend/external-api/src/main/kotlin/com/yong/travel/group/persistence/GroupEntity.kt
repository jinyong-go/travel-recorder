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
 * 조회 전용 공유 대상 목록. 한 번 만든 그룹을 여러 여행에 반복 재사용한다.
 *
 * 그룹은 권한 범위가 아니다. 멤버는 이 그룹으로 공유 설정된 여행만 볼 수 있을 뿐,
 * 소유자나 다른 멤버의 나머지 여행에는 접근할 수 없다. 편집 권한은 어떤 경우에도 주지 않는다.
 *
 * `group` 은 SQL 예약어라 테이블명만 `share_group` 으로 둔다.
 */
@Entity
@Table(name = "share_group")
class GroupEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: UserEntity,

    @Column(nullable = false, length = 30)
    var name: String,

    /** 어떤 사람들을 모아 둔 목록인지 적어 두는 설명. 소유자와 멤버 모두에게 보인다. */
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
        /** 소유자를 포함한 그룹당 최대 인원. 소규모 동행·가족 단위 공유를 전제로 한 값이다. */
        const val MEMBER_LIMIT = 5

        /**
         * 한 사용자가 소유할 수 있는 그룹 수, 소유하지 않고 멤버로 참여할 수 있는 그룹 수의 상한.
         *
         * 둘을 나눈 것은 하나로 합치면 그룹을 많이 만든 사람이 초대를 수락하지 못하는 식으로 두 행위가 서로를
         * 막기 때문이다. 합쳐서 내 그룹 목록의 크기를 묶는다.
         */
        const val OWNED_GROUP_LIMIT = 50
        const val JOINED_GROUP_LIMIT = 50
    }
}
