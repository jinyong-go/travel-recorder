package com.yong.travel.review.repository

import com.yong.travel.review.domain.Review
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ReviewRepository : JpaRepository<Review, Long> {
    fun findByPlaceId(placeId: Long, pageable: Pageable): Page<Review>

    fun findByPlaceIdAndUserId(placeId: Long, userId: Long): Review?

    fun countByPlaceId(placeId: Long): Long

    @Query("select coalesce(avg(r.score), 0.0) from Review r where r.place.id = :placeId")
    fun averageScore(@Param("placeId") placeId: Long): Double

    /**
     * soft delete 된 행까지 포함해 조회한다.
     * `(place_id, user_id)` 유니크 제약 때문에 삭제된 행이 남아 있으면 같은 사용자의 재등록 insert 가
     * 실패하므로, upsert 는 이 메서드로 기존 행을 찾아 되살린다.
     * 엔티티의 `@SQLRestriction` 은 HQL 로 우회할 수 없어 네이티브 쿼리를 쓴다.
     */
    @Query(
        value = "select * from reviews where place_id = :placeId and user_id = :userId",
        nativeQuery = true,
    )
    fun findAnyByPlaceIdAndUserId(@Param("placeId") placeId: Long, @Param("userId") userId: Long): Review?

    /** 여행지 soft delete 시 종속 리뷰를 함께 숨긴다. 벌크 UPDATE 라 `@PreUpdate` 가 돌지 않아 updatedAt 도 직접 채운다. */
    @Modifying
    @Query(
        "update Review r set r.deletedAt = :deletedAt, r.updatedAt = :deletedAt " +
            "where r.place.id = :placeId and r.deletedAt is null",
    )
    fun softDeleteByPlaceId(@Param("placeId") placeId: Long, @Param("deletedAt") deletedAt: Instant): Int
}
