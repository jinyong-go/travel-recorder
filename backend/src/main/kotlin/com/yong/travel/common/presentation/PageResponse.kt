package com.yong.travel.common.presentation

import com.yong.travel.common.domain.PageResult

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /**
     * 내용만 옮기고 페이지 메타데이터는 그대로 둔다.
     *
     * 서비스가 도메인 객체 페이지를 돌려주고 컨트롤러가 응답 DTO 로 바꾸는 경로에 쓴다.
     * 건수·페이지 번호는 조회 시점에 정해진 값이라 변환으로 달라져서는 안 된다.
     */
    fun <R> map(transform: (T) -> R): PageResponse<R> =
        PageResponse(content.map(transform), page, size, totalElements, totalPages)

    companion object {
        fun <T> of(result: PageResult<T>): PageResponse<T> =
            PageResponse(result.content, result.page, result.size, result.totalElements, result.totalPages)
    }
}
