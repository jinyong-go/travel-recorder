package com.yong.travel.common.persistence

import com.yong.travel.common.domain.PageResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/** 목록 페이지 크기. */
const val DEFAULT_PAGE_SIZE = 10

/**
 * 페이지 번호로 페이지 요청을 만든다. 크기는 서버가 정한다.
 *
 * @param page 0부터 시작. 음수는 0으로 본다
 */
fun listPageRequest(page: Int, sort: Sort = Sort.unsorted()): PageRequest =
    PageRequest.of(page.coerceAtLeast(0), DEFAULT_PAGE_SIZE, sort)

/** 조회 결과를 서비스 반환형으로 바꾼다. */
fun <T : Any> Page<T>.toPageResult(): PageResult<T> =
    PageResult(content, number, size, totalElements)
