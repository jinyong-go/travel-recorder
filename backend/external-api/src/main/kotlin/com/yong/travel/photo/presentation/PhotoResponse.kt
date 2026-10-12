package com.yong.travel.photo.presentation

import com.yong.travel.photo.domain.Photo
import java.util.UUID

/** 사진 한 장 — 식별자와 서버가 만든 접근 URL. */
data class PhotoResponse(
    val id: UUID,
    val url: String,
) {
    companion object {
        /**
         * 사진 → 응답.
         *
         * @param photo 접근 URL 이 붙은 사진
         * @return 응답 DTO
         */
        fun from(photo: Photo) = PhotoResponse(photo.id, photo.url)
    }
}
