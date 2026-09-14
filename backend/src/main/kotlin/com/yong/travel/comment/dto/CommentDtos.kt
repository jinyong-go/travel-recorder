package com.yong.travel.comment.dto

import com.yong.travel.auth.dto.UserResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CommentRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,
)

data class CommentResponse(
    val id: Long,
    val author: UserResponse,
    val content: String,
    val createdAt: Instant,
)
