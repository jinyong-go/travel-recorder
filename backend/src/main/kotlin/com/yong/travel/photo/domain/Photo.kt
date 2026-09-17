package com.yong.travel.photo.domain

import com.yong.travel.record.domain.VisitRecord
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
 * 기록에 첨부된 사진의 메타데이터. 파일 본체는 app.storage 설정 위치에 저장한다.
 *
 * 업로더 컬럼은 두지 않는다. 사진을 올릴 수 있는 사람이 기록 작성자뿐이라
 * `record.author` 와 항상 같은 값이 되고, 같은 사실을 두 곳에 적을 이유가 없다.
 */
@Entity
@Table(name = "photos")
class Photo(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    var record: VisitRecord,

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
