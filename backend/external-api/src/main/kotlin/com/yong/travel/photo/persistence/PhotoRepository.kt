package com.yong.travel.photo.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** 사진 메타데이터 조회. 목록용 일괄 조회와 커버 지정 확인, 서빙 전 열람 판정([PhotoSpecifications])을 함께 둔다. */
interface PhotoRepository : JpaRepository<PhotoEntity, UUID>, JpaSpecificationExecutor<PhotoEntity> {
    /** 한 기록의 사진을 등록순으로 읽는다. */
    fun findByRecordIdOrderByCreatedAtAsc(recordId: Long): List<PhotoEntity>

    /**
     * 여러 기록의 사진을 한 번에 읽는다.
     *
     * 기록 목록이 썸네일과 사진 수를 항목마다 따로 묻지 않도록 두었다. 페이지 크기만큼 쿼리가
     * 늘던 자리이며, 호출부가 기록 id 로 묶어 쓴다.
     *
     * @return 등록순 사진 목록
     */
    fun findByRecordIdInOrderByCreatedAtAsc(recordIds: Collection<Long>): List<PhotoEntity>

    /**
     * 그 기록에 달린 사진인지 함께 확인하며 읽는다.
     *
     * @return 사진. 없거나 그 기록의 사진이 아니면 null
     */
    fun findByIdAndRecordId(id: UUID, recordId: Long): PhotoEntity?

    /**
     * 커버로 지정할 수 있는 사진인지 확인한다 — 그 여행의 하위 기록에 속한 사진만이다.
     *
     * 삭제된 기록의 사진은 제외한다. 사진 행은 기록이 soft delete 되어도 남아 있어서,
     * 조건을 빼면 지워진 기록의 사진이 여행 커버가 될 수 있다.
     *
     * @return 사진. 그 여행의 살아 있는 기록에 속하지 않으면 null
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

    /** 한 기록에 달린 사진 id 전부를 읽는다. 커버 해제 판정에 쓴다. */
    @Query("select p.id from PhotoEntity p where p.record.id = :recordId")
    fun findIdsByRecordId(@Param("recordId") recordId: Long): List<UUID>
}
