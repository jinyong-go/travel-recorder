package com.yong.travel.photo.repository

import com.yong.travel.photo.domain.Photo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoRepository : JpaRepository<Photo, Long> {
    fun findByRecordIdOrderByCreatedAtAsc(recordId: Long): List<Photo>

    fun findByIdAndRecordId(id: Long, recordId: Long): Photo?

    /**
     * 커버로 지정할 수 있는 사진인지 확인한다 — 그 여행의 하위 기록에 속한 사진만이다 (명세 §4.3.2).
     *
     * 삭제된 기록의 사진은 제외한다. 사진 행은 기록이 soft delete 되어도 남아 있어서,
     * 조건을 빼면 지워진 기록의 사진이 여행 커버가 될 수 있다.
     */
    @Query(
        """
        select p from Photo p
        where p.id = :photoId
          and p.record.trip.id = :tripId
          and p.record.deletedAt is null
        """,
    )
    fun findByIdAndTripId(@Param("photoId") photoId: Long, @Param("tripId") tripId: Long): Photo?

    /** 한 기록에 달린 사진 id 전부. 커버 해제 판정에 쓴다. */
    @Query("select p.id from Photo p where p.record.id = :recordId")
    fun findIdsByRecordId(@Param("recordId") recordId: Long): List<Long>
}
