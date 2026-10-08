package com.yong.travel.photo.presentation

import com.yong.travel.photo.domain.Photo
import java.util.UUID

data class PhotoResponse(
    val id: UUID,
    val url: String,
)

fun Photo.toResponse(): PhotoResponse = PhotoResponse(id, url)
