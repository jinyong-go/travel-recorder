package com.yong.travel.photo.persistence

import com.yong.travel.record.persistence.TripRecordEntity
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
 * 기록에 첨부된 사진의 메타데이터. 바이너리는 [PhotoDataEntity] 에 따로 둔다.
 *
 * 업로더 컬럼은 두지 않는다. 사진을 올릴 수 있는 사람이 소속 여행의 소유자뿐이라
 * `record.trip.owner` 와 항상 같은 값이 되고, 같은 사실을 두 곳에 적을 이유가 없다.
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
    /** 무작위 UUID(v4). 사진 URL 에 그대로 실리므로 순번처럼 추측되면 안 된다. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
