package com.yong.travel.photo.persistence

import com.yong.travel.photo.domain.PhotoContent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** 사진 바이너리 저장소. */
interface PhotoDataRepository : JpaRepository<PhotoDataEntity, UUID> {
    /** 사진의 바이너리와 형식을 읽는다. 없으면 null. */
    @Query(
        """
        select new com.yong.travel.photo.domain.PhotoContent(d.photo.contentType, d.data)
        from PhotoDataEntity d
        where d.photoId = :photoId
        """,
    )
    fun findContentById(@Param("photoId") photoId: UUID): PhotoContent?

    /**
     * 기록에 달린 사진의 바이너리를 지운다.
     * JPQL 은 삭제된 기록을 걸러 내 호출 순서에 따라 아무것도 못 지울 수 있어 네이티브 SQL 로 쓴다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "DELETE FROM photo_data WHERE photo_id IN (SELECT id FROM photos WHERE record_id = :recordId)",
        nativeQuery = true,
    )
    fun deleteByRecordId(@Param("recordId") recordId: Long): Int

    /** 여행의 모든 기록에 달린 사진의 바이너리를 지운다. 네이티브 SQL 인 이유는 [deleteByRecordId] 와 같다. */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            DELETE FROM photo_data WHERE photo_id IN (
                SELECT p.id FROM photos p JOIN trip_records r ON r.id = p.record_id WHERE r.trip_id = :tripId
            )
        """,
        nativeQuery = true,
    )
    fun deleteByTripId(@Param("tripId") tripId: Long): Int
}
