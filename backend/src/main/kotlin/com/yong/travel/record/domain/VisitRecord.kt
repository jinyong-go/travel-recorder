package com.yong.travel.record.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.tag.domain.Tag
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
 * 방문 기록 — 서비스의 유일한 저장 단위이자 개인 데이터다.
 *
 * 장소는 외부(네이버 지역 검색)에서 조회할 뿐 자체 저장 단위로 두지 않으므로,
 * 장소명·주소·좌표·원본 링크는 등록 시점의 스냅샷으로 이 행에 복사해 둔다.
 * 외부에서 장소 정보가 바뀌어도 기록은 "그때 내가 방문한 곳"을 그대로 남긴다.
 *
 * 같은 장소를 다시 방문하면 매번 새 행을 만든다. 방문 시점마다 사진·메모·평점이 다르기 때문에
 * 중복이 아니며, 그래서 중복 방지 제약도 두지 않는다.
 */
@Entity
@Table(name = "visit_records")
@SQLRestriction("deleted_at is null")
class VisitRecord(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: User,

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: Visibility = Visibility.PRIVATE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "visit_record_tags",
        joinColumns = [JoinColumn(name = "record_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    var tags: MutableSet<Tag> = mutableSetOf()

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

    fun softDelete() {
        if (deletedAt == null) deletedAt = Instant.now()
    }
}
