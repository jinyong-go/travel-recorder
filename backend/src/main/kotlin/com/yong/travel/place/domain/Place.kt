package com.yong.travel.place.domain

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

@Entity
@Table(name = "places")
@SQLRestriction("deleted_at is null")
class Place(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

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

    @Column(length = 1000)
    var memo: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "place_tags",
        joinColumns = [JoinColumn(name = "place_id")],
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
