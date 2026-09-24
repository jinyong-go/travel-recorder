package com.yong.travel.tag.dto

import com.yong.travel.tag.domain.Tag

data class TagResponse(
    val id: Long,
    val name: String,
)

fun Tag.toResponse(): TagResponse = TagResponse(id, name)
