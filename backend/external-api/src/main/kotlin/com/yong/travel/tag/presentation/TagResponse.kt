package com.yong.travel.tag.presentation

import com.yong.travel.tag.domain.Tag

/** 자동완성 후보 태그 하나. */
data class TagResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        /**
         * 태그 → 응답.
         *
         * @param tag 자동완성 후보 태그
         * @return 응답 DTO
         */
        fun from(tag: Tag) = TagResponse(tag.id, tag.name)
    }
}
