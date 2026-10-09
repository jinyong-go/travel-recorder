package com.yong.travel.photo.persistence

import com.yong.travel.photo.domain.PhotoContent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PhotoDataRepository : JpaRepository<PhotoDataEntity, UUID> {
    /** 서빙에 필요한 Content-Type 과 바이트를 한 번에 읽는다. 없으면 null. */
    @Query(
        """
        select new com.yong.travel.photo.domain.PhotoContent(d.photo.contentType, d.data)
        from PhotoDataEntity d
        where d.photoId = :photoId
        """,
    )
    fun findContentById(@Param("photoId") photoId: UUID): PhotoContent?
}
