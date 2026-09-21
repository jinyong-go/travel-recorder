package com.yong.travel.trip.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.photo.domain.Photo
import com.yong.travel.trip.domain.Visibility
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
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

/**
 * 여행 — 여행 기록의 상위 그룹이자 공유의 단위.
 *
 * **공개 범위 컬럼은 이 엔티티에만 존재한다** (명세 §3.1). 하위 기록은 범위를 갖지 않고 소속
 * 여행의 값을 그대로 따르므로, 여행 하나만 바꾸면 하위 기록 전체의 노출이 함께 바뀐다.
 *
 * 소유자는 하위 기록 전부의 작성자이기도 하다. 편집 권한 판정의 유일한 근거다.
 */
@Entity
@Table(name = "trips")
@SQLRestriction("deleted_at is null")
class Trip(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false)
    var startDate: LocalDate,

    /** startDate 와 같은 날이면 당일치기다. 미래 날짜를 막지 않는다 — 여행 중 등록이 정상 사용이다. */
    @Column(nullable = false)
    var endDate: LocalDate,

    /** 본인을 포함한 사람 수. 계정·공유 그룹과 무관하며 상한을 두지 않는다 (명세 §3.1). */
    @Column(nullable = false)
    var headcount: Int,

    /** 원 단위. null 은 "예산 정보 없음" 이고 0 과 구분된다 (명세 §3.1). */
    var budget: Long? = null,

    @Column(length = 1000)
    var memo: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: Visibility = Visibility.PRIVATE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    /**
     * 커버 사진. 그 여행의 하위 기록에 속한 사진만 지정할 수 있다 (명세 §4.3.2).
     *
     * 생성자 파라미터가 아닌 이유는 생성 시점에 고를 사진이 아직 존재할 수 없기 때문이다.
     *
     * **DB 에 외래키를 두지 않는다.** trips → photos → trip_records → trips 로 순환 참조가
     * 생기는데, schema.sql 은 인라인 FK 만 쓰고 ALTER TABLE 에는 IF NOT EXISTS 가 없어
     * 재기동 시 실패한다. 대신 사진을 지울 때 애플리케이션이 커버 지정을 해제한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    var coverPhoto: Photo? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    /** soft delete 시각. null 이면 살아 있는 행이다 (@SQLRestriction 으로 조회에서 자동 제외). */
    var deletedAt: Instant? = null
        protected set

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** 하위 기록도 함께 지워야 하지만, 그것은 서비스의 몫이다 — 엔티티는 자기 상태만 바꾼다. */
    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }

    /**
     * 지정된 사진 중 하나가 커버였다면 커버 지정을 푼다.
     *
     * `cover_photo_id` 에 외래키를 두지 않기로 했으므로(명세 §3), 사진이 이 여행에서 사라지는
     * **모든 경로**가 이 처리를 직접 해야 한다 — 사진 삭제, 기록 삭제, 기록의 소속 여행 변경.
     * 빠뜨리면 없는 사진을 가리키는 커버가 남아 여행 조회가 깨진다. 그래서 경로마다 흩어 놓지
     * 않고 상태를 가진 이곳에 모아 둔다.
     */
    fun clearCoverIfAmong(photoIds: Collection<Long>) {
        val current = coverPhoto?.id ?: return
        if (current in photoIds) coverPhoto = null
    }
}
