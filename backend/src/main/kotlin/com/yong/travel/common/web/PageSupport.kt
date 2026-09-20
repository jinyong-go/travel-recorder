package com.yong.travel.common.web

import org.springframework.data.domain.PageRequest

/** 목록 기본 페이지 크기 (명세 §4.1). 장소 검색만 원본 API 제약으로 다른 값을 쓴다 (§4.5). */
const val DEFAULT_PAGE_SIZE = 10

/**
 * 목록 요청의 `page` 를 페이지 요청으로 바꾼다.
 *
 * 크기는 서버가 정하므로 요청에서 받지 않으며, 음수 `page` 는 0으로 본다 (명세 §4.1).
 */
fun listPageRequest(page: Int): PageRequest = PageRequest.of(page.coerceAtLeast(0), DEFAULT_PAGE_SIZE)
