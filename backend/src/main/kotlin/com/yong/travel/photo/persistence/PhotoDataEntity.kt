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
 * 사진 바이너리. [PhotoEntity] 와 1:1 이며 id 를 공유한다.
 *
 * 메타데이터와 테이블을 나눈 것은 기록 목록·여행 커버처럼 사진을 읽는 경로에 수 MB 가 딸려 오지
 * 않게 하기 위해서다. 컬럼 단위 지연 로딩은 바이트코드 enhancement 없이는 동작하지 않는다.
 * 사진 행이 지워지면 DB 의 ON DELETE CASCADE 가 이 행도 지운다.
 */
@Entity
@Table(name = "photo_data")
class PhotoDataEntity(
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    var photo: PhotoEntity,

    // @Lob 을 붙이지 않는다. PostgreSQL 에서 oid(large object)로 매핑되어 행을 지워도 본체가 남는다.
    @Column(nullable = false)
    var data: ByteArray,
) {
    @Id
    var photoId: UUID? = null
        protected set
}
