package com.yong.travel.common.domain

/** 서비스가 돌려주는 목록 한 페이지. */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    /** 전체 건수와 페이지 크기로 계산한다. */
    val totalPages: Int
        get() = if (size == 0) 0 else ((totalElements + size - 1) / size).toInt()

    /** 내용만 바꾸고 페이지 메타데이터는 그대로 옮긴다. */
    fun <R> map(transform: (T) -> R): PageResult<R> =
        PageResult(content.map(transform), page, size, totalElements)
}
