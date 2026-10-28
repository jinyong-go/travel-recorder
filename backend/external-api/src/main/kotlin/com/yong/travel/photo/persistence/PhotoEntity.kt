package com.yong.travel.photo.persistence

import com.yong.travel.trip.record.persistence.TripRecordEntity
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
import java.util.UUID

/**
 * 기록에 첨부된 사진의 메타데이터. 바이너리는 [PhotoDataEntity] 에 있다.
 * 사진은 여행 소유자만 올리므로 업로더 컬럼은 따로 두지 않는다.
 */
@Entity
@Table(name = "photos")
class PhotoEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    var record: TripRecordEntity,

    @Column(nullable = false)
    var originalFileName: String,

    @Column(nullable = false)
    var contentType: String,

    @Column(nullable = false)
    var fileSizeBytes: Long,
) {
    /** URL 에 실리므로 추측할 수 없는 무작위 UUID 를 쓴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
