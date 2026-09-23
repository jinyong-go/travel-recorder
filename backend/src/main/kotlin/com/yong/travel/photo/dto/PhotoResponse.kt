package com.yong.travel.photo.dto

import com.yong.travel.photo.domain.PhotoRef

data class PhotoResponse(
    val id: Long,
    val url: String,
)

fun PhotoRef.toResponse(): PhotoResponse = PhotoResponse(id, url)
