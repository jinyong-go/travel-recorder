package com.yong.travel.photo.presentation

import com.yong.travel.photo.domain.Photo
import java.util.UUID

data class PhotoResponse(
    val id: UUID,
    val url: String,
) {
    companion object {
        fun from(photo: Photo) = PhotoResponse(photo.id, photo.url)
    }
}
