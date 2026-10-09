package com.yong.travel.common.persistence

import com.yong.travel.common.domain.PageResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/** 목록 기본 페이지 크기. 장소 검색만 원본 API 제약으로 다른 값을 쓴다. */
const val DEFAULT_PAGE_SIZE = 10

/**
 * 목록 요청의 `page` 를 페이지 요청으로 바꾼다.
 *
 * 크기는 서버가 정하므로 요청에서 받지 않으며, 음수 `page` 는 0으로 본다.
 */
fun listPageRequest(page: Int, sort: Sort = Sort.unsorted()): PageRequest =
    PageRequest.of(page.coerceAtLeast(0), DEFAULT_PAGE_SIZE, sort)

/** 조회 결과 → 서비스 반환형. Spring Data 타입이 서비스 밖으로 나가지 않게 하는 경계다. */
fun <T : Any> Page<T>.toPageResult(): PageResult<T> =
    PageResult(content, number, size, totalElements)
