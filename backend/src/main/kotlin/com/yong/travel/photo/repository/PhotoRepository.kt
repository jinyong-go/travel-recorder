package com.yong.travel.photo.repository

import com.yong.travel.photo.domain.Photo
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoRepository : JpaRepository<Photo, Long> {
    fun findByRecordIdOrderByCreatedAtAsc(recordId: Long): List<Photo>
    fun findByIdAndRecordId(id: Long, recordId: Long): Photo?
}
