package com.yong.travel.rating.repository

import com.yong.travel.rating.domain.Rating
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RatingRepository : JpaRepository<Rating, Long> {
    fun findByPlaceIdAndUserId(placeId: Long, userId: Long): Rating?

    fun countByPlaceId(placeId: Long): Long

    @Query("select coalesce(avg(r.score), 0.0) from Rating r where r.place.id = :placeId")
    fun averageScore(@Param("placeId") placeId: Long): Double
}
