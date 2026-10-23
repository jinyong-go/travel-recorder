package com.yong.travel.photo.persistence

import com.yong.travel.photo.domain.PhotoContent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** 사진 바이너리 조회. 저장은 기본 `save` 를 쓴다. */
interface PhotoDataRepository : JpaRepository<PhotoDataEntity, UUID> {
    /**
     * 서빙에 필요한 Content-Type 과 바이트를 한 번에 읽는다.
     *
     * @return 바이너리와 Content-Type. 없으면 null
     */
    @Query(
        """
        select new com.yong.travel.photo.domain.PhotoContent(d.photo.contentType, d.data)
        from PhotoDataEntity d
        where d.photoId = :photoId
        """,
    )
    fun findContentById(@Param("photoId") photoId: UUID): PhotoContent?

    /**
     * 기록에 달린 사진의 바이너리를 지운다. 기록 soft delete 에 딸려 간다.
     *
     * 네이티브 SQL 로 둔다. JPQL 로 사진 → 기록을 따라가면 TripRecordEntity 의 @SQLRestriction 이 붙어,
     * 기록을 먼저 soft delete 한 뒤에 부르면 아무것도 지우지 못한다. 호출 순서에 기대지 않게 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "DELETE FROM photo_data WHERE photo_id IN (SELECT id FROM photos WHERE record_id = :recordId)",
        nativeQuery = true,
    )
    fun deleteByRecordId(@Param("recordId") recordId: Long): Int

    /**
     * 여행의 하위 기록 전부에 달린 사진의 바이너리를 지운다. 여행 soft delete 에 딸려 간다.
     * 네이티브 SQL 로 두는 이유는 [deleteByRecordId] 와 같다.
     */
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
