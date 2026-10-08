package com.yong.travel.photo.domain

import java.util.UUID

/**
 * 응답에 실리는 사진 한 장 — 식별자와 이미 만들어진 접근 URL.
 *
 * `url` 은 서버가 만들어 준 값을 그대로 쓴다. 클라이언트가 id 로 URL 을 조립하지 않는다 (명세 §4.6).
 */
data class Photo(
    val id: UUID,
    val url: String,
)
