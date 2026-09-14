package com.yong.travel.comment.repository

import com.yong.travel.comment.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByPlaceId(placeId: Long, pageable: Pageable): Page<Comment>
    fun findByIdAndPlaceId(id: Long, placeId: Long): Comment?
}
