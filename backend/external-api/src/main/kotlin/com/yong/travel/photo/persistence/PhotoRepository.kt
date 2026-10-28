package com.yong.travel.photo.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** 사진 메타데이터 저장소. */
interface PhotoRepository : JpaRepository<PhotoEntity, UUID>, JpaSpecificationExecutor<PhotoEntity> {
    /** 한 기록의 사진을 등록순으로 읽는다. */
    fun findByRecordIdOrderByCreatedAtAsc(recordId: Long): List<PhotoEntity>

    /** 여러 기록의 사진을 등록순으로 한 번에 읽는다. */
    fun findByRecordIdInOrderByCreatedAtAsc(recordIds: Collection<Long>): List<PhotoEntity>

    /** 기록에 달린 사진을 찾는다. 없거나 그 기록의 사진이 아니면 null. */
    fun findByIdAndRecordId(id: UUID, recordId: Long): PhotoEntity?

    /**
     * 여행의 기록에 속한 사진을 찾는다. 없으면 null.
     * 기록이 지워져도 사진 행은 남으므로 지워진 기록의 사진은 직접 걸러 낸다.
     */
    @Query(
        """
        select p from PhotoEntity p
        where p.id = :photoId
          and p.record.trip.id = :tripId
          and p.record.deletedAt is null
        """,
    )
    fun findByIdAndTripId(@Param("photoId") photoId: UUID, @Param("tripId") tripId: Long): PhotoEntity?

    /** 기록에 달린 사진 id 를 모두 읽는다. */
    @Query("select p.id from PhotoEntity p where p.record.id = :recordId")
    fun findIdsByRecordId(@Param("recordId") recordId: Long): List<UUID>
}
