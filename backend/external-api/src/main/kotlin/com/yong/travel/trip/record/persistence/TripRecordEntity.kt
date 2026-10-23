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
 * 여행 기록 — 여행에 속한 방문 한 건이다.
 *
 * **작성자도 공개 범위도 두지 않는다.** 둘 다 소속 여행에서 파생되며, 두 곳에 적으면 어긋날 수 있다.
 *
 * 장소는 자체 저장 단위가 아니라서 장소명·주소·좌표·원본 링크를 등록 시점의 스냅샷으로 복사해 둔다.
 * 같은 장소를 다시 방문하면 새 행을 만든다. 방문마다 사진·메모·평점이 달라 중복 방지 제약을 두지 않는다.
 */
@Entity
@Table(name = "trip_records")
@SQLRestriction("deleted_at is null")
class TripRecordEntity(
    /**
     * 소속 여행. 작성자와 공개 범위의 유일한 출처다.
     *
     * NOT NULL 이다 — 여행에 속하지 않는 기록은 누가 볼 수 있는지 판정할 근거가 없다.
     * 다른 여행으로 옮기는 것은 이 참조를 바꾸는 것으로 처리한다.
     */
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

    /** 네이버 지역 검색 결과 원본 링크. 지역 검색 API 는 안정적인 장소 ID 를 주지 않아 참고용으로만 쓴다. */
    var externalLink: String? = null,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    /** 0.5 ~ 5.0, 0.5 단위. 0.5 단위 값은 이진 부동소수점으로 정확히 표현되므로 오차 없이 비교·정렬된다. */
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

    // 만든 직후에는 수정 시각이 생성 시각과 같아야 한다. 따로 now() 를 부르면 몇 µs 어긋나
    // 새로 만든 행도 "수정됨" 으로 보인다.
    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    /** soft delete 시각. null 이면 살아 있는 행이다 (@SQLRestriction 으로 조회에서 자동 제외). */
    var deletedAt: Instant? = null
        protected set

    /** 수정 시각을 갱신한다. flush 시점에 JPA 가 부른다. */
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** 삭제 시각을 남긴다. 이미 지워진 기록이면 처음 시각을 그대로 둔다. */
    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }

    /**
     * 태그를 주어진 집합으로 맞춘다.
     *
     * 컬렉션을 새 Set 으로 바꿔 끼우면 Hibernate 가 조인 테이블 행을 전부 지우고 다시 넣는다. 그래서
     * 빠진 것만 지우고 새것만 더해, 바뀐 행만 DELETE·INSERT 되게 한다. [TagEntity] 는 equals 를
     * 재정의하지 않으므로 같은 영속성 컨텍스트에서 읽은 태그 엔티티를 넘겨야 한다.
     */
    fun replaceTags(newTags: Set<TagEntity>) {
        tags.retainAll(newTags)
        tags.addAll(newTags)
    }
}
