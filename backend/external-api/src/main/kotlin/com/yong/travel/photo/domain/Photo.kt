package com.yong.travel.photo.domain

import java.util.UUID

/** 사진 id 와 접근 URL. */
data class Photo(
    val id: UUID,
    val url: String,
)
