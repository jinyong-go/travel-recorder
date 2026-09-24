package com.yong.travel.photo.dto

import com.yong.travel.photo.domain.Photo

data class PhotoResponse(
    val id: Long,
    val url: String,
)

fun Photo.toResponse(): PhotoResponse = PhotoResponse(id, url)
