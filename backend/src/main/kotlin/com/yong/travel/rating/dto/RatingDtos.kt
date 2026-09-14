package com.yong.travel.rating.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

data class RatingRequest(
    @field:Min(1)
    @field:Max(5)
    val score: Int,
)

data class RatingResponse(
    val score: Int,
    val updatedAt: Instant,
)
