package com.yong.travel.tag.dto

import com.yong.travel.tag.domain.TagRef

data class TagResponse(
    val id: Long,
    val name: String,
)

fun TagRef.toResponse(): TagResponse = TagResponse(id, name)
