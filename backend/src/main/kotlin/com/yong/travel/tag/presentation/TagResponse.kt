package com.yong.travel.tag.presentation

import com.yong.travel.tag.domain.Tag

data class TagResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(tag: Tag) = TagResponse(tag.id, tag.name)
    }
}
