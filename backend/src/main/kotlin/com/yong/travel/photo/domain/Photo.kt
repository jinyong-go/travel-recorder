package com.yong.travel.photo.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.place.domain.Place
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

@Entity
@Table(name = "photos")
class Photo(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    var place: Place,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    var uploader: User,

    /** 저장소 내 상대 경로(파일시스템) 또는 향후 S3 object key. */
    @Column(nullable = false)
    var storageKey: String,

    @Column(nullable = false)
    var originalFileName: String,

    @Column(nullable = false)
    var contentType: String,

    @Column(nullable = false)
    var fileSizeBytes: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
