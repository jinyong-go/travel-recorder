package com.yong.travel.trip.persistence

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.trip.domain.TripUpdateCommand
import com.yong.travel.trip.domain.TripVisibility
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
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 여행. 기록을 묶고 공개 범위를 정하는 단위다.
 * 기록은 소속 여행의 공개 범위를 따르고, 여행 소유자만 기록을 편집할 수 있다.
 */
@Entity
@Table(name = "trips")
@SQLRestriction("deleted_at is null")
class TripEntity(
    owner: UserEntity,
    name: String,
    startDate: LocalDate,
    endDate: LocalDate,
    headcount: Int,
    budget: Long? = null,
    memo: String? = null,
    visibility: TripVisibility = TripVisibility.PRIVATE,
) {
    /** 소유자. 바뀌지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: UserEntity = owner
        protected set

    /** 앞뒤 공백을 지워 저장한다. */
    @Column(nullable = false, length = 50)
    var name: String = name.trim()
        protected set

    @Column(nullable = false)
    var startDate: LocalDate = startDate
        protected set

    /** 시작일과 같으면 당일치기다. 여행 중에도 등록하므로 미래 날짜를 허용한다. */
    @Column(nullable = false)
    var endDate: LocalDate = endDate
        protected set

    /** 본인을 포함한 인원. 공유 그룹과는 상관없다. */
    @Column(nullable = false)
    var headcount: Int = headcount
        protected set

    /** 원 단위. null 은 정보 없음이고 0 과 다르다. */
    var budget: Long? = budget
        protected set

    @Column(length = 1000)
    var memo: String? = memo
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: TripVisibility = visibility
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    /**
     * 커버 사진. 이 여행의 기록에 속한 사진만 지정할 수 있다.
     * 외래키가 없으므로 사진이 사라지면 [clearCoverIfAmong] 으로 직접 해제한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    var coverPhoto: PhotoEntity? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    // 새 행이 수정된 것으로 보이지 않도록 생성 시각과 같은 값으로 시작한다.
    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    /** 동시 수정 감지용 버전. */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    /** 삭제 시각. 삭제된 행은 조회에서 빠진다. */
    var deletedAt: Instant? = null
        protected set

    /** 수정 시각을 갱신한다. */
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** 기본 정보를 바꾼다. 공개 범위는 [changeVisibility] 로 바꾼다. */
    fun update(command: TripUpdateCommand) {
        name = command.name.trim()
        startDate = command.startDate
        endDate = command.endDate
        headcount = command.headcount
        budget = command.budget
        memo = command.memo
    }

    /** 공개 범위를 바꾼다. 공유 그룹은 따로 맞춰야 하며, 공유 그룹만 바뀌어도 수정으로 본다. */
    fun changeVisibility(visibility: TripVisibility) {
        this.visibility = visibility
        // 공유만 바뀌면 이 행이 그대로라 버전 비교가 일어나지 않으므로 수정 시각을 직접 바꾼다.
        updatedAt = Instant.now()
    }

    /** 커버 사진을 지정한다. null 이면 해제한다. */
    fun changeCover(photo: PhotoEntity?) {
        coverPhoto = photo
    }

    /** 여행을 삭제 상태로 바꾼다. 기록은 따로 지워야 한다. */
    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }

    /**
     * 주어진 사진 중 하나가 커버면 커버를 해제한다.
     * 사진 삭제·기록 삭제·기록 이동처럼 사진이 여행에서 빠지는 곳에서 모두 불러야 한다.
     */
    fun clearCoverIfAmong(photoIds: Collection<UUID>) {
        val current = coverPhoto?.id ?: return
        if (current in photoIds) coverPhoto = null
    }
}
