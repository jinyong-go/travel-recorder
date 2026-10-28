package com.yong.travel.trip.record.persistence

import com.yong.travel.tag.persistence.TagEntity
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.record.domain.Category
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * 여행 기록. 여행에 속한 방문 한 건이다.
 * 작성자와 공개 범위는 소속 여행을 따르고, 장소 정보는 등록할 때의 값을 복사해 둔다.
 */
@Entity
@Table(name = "trip_records")
@SQLRestriction("deleted_at is null")
class TripRecordEntity(
    /** 소속 여행. 여행이 없으면 누가 볼 수 있는지 정할 수 없어 반드시 있어야 한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: TripEntity,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: Category,

    @Column(nullable = false)
    var address: String,

    var roadAddress: String? = null,

    /** 장소 검색 결과의 원본 링크. 참고용이다. */
    var externalLink: String? = null,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    /** 0.5 ~ 5.0, 0.5 단위. */
    @Column(nullable = false)
    var rating: Double,

    @Column(length = 1000)
    var memo: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "trip_record_tags",
        joinColumns = [JoinColumn(name = "record_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    var tags: MutableSet<TagEntity> = mutableSetOf()

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    // 새 행이 수정된 것으로 보이지 않도록 생성 시각과 같은 값으로 시작한다.
    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    /** 삭제 시각. 삭제된 행은 조회에서 빠진다. */
    var deletedAt: Instant? = null
        protected set

    /** 수정 시각을 갱신한다. */
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** 기록을 삭제 상태로 바꾼다. */
    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }

    /**
     * 태그를 주어진 집합으로 바꾼다. 컬렉션을 통째로 바꾸면 연결 행이 모두 다시 써지므로 차이만 반영한다.
     * 태그는 참조로 비교하므로 같은 영속성 컨텍스트에서 읽은 엔티티를 넘겨야 한다.
     */
    fun replaceTags(newTags: Set<TagEntity>) {
        tags.retainAll(newTags)
        tags.addAll(newTags)
    }
}
