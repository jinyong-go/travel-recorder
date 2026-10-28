package com.yong.travel.common.presentation

import com.yong.travel.common.domain.PageResult

/** 목록 응답. 한 페이지의 내용과 페이지 정보를 담는다. */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /** 내용만 바꾸고 페이지 정보는 그대로 둔다. */
    fun <R> map(transform: (T) -> R): PageResponse<R> =
        PageResponse(content.map(transform), page, size, totalElements, totalPages)

    companion object {
        /** 서비스 결과를 응답으로 옮긴다. */
        fun <T> of(result: PageResult<T>): PageResponse<T> =
            PageResponse(result.content, result.page, result.size, result.totalElements, result.totalPages)
    }
}
