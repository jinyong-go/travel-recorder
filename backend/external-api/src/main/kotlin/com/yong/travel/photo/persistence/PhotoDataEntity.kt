package com.yong.travel.photo.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * 사진 바이너리. [PhotoEntity] 와 id 를 공유한다.
 * 사진 목록을 읽을 때 바이너리가 딸려 오지 않도록 테이블을 나눴다.
 */
@Entity
@Table(name = "photo_data")
class PhotoDataEntity(
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    var photo: PhotoEntity,

    // @Lob 을 쓰면 PostgreSQL 에서 행을 지워도 데이터가 남는다.
    @Column(nullable = false)
    var data: ByteArray,
) {
    @Id
    var photoId: UUID? = null
        protected set
}
