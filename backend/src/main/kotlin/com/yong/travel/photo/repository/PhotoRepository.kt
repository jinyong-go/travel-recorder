package com.yong.travel.photo.repository

import com.yong.travel.photo.domain.Photo
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoRepository : JpaRepository<Photo, Long> {
    fun findByPlaceIdOrderByCreatedAtAsc(placeId: Long): List<Photo>
    fun findByIdAndPlaceId(id: Long, placeId: Long): Photo?
}
