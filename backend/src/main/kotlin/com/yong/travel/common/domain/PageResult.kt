package com.yong.travel.common.domain

/**
 * 서비스가 돌려주는 목록 한 페이지. 저장 수단(Spring Data)을 모르는 쪽의 표현이다.
 *
 * 컨트롤러가 Spring Data 타입에 묶이지 않게 하려고 둔다. 건수·페이지 번호는 조회 시점에 정해진
 * 값이라, 내용을 바꾸는 [map] 은 메타데이터를 그대로 옮긴다.
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    /** 건수와 크기에서 나오는 값이라 따로 들고 다니지 않는다. 둘이 어긋날 자리를 만들지 않는다. */
    val totalPages: Int
        get() = if (size == 0) 0 else ((totalElements + size - 1) / size).toInt()

    fun <R> map(transform: (T) -> R): PageResult<R> =
        PageResult(content.map(transform), page, size, totalElements)
}
