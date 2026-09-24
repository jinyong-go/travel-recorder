package com.yong.travel.photo.domain

/**
 * 응답에 실리는 사진 한 장 — 식별자와 이미 만들어진 접근 URL.
 *
 * `storageKey` 를 담지 않는다. 저장소 내부 경로는 URL 을 만드는 데만 쓰이며
 * 밖으로 나갈 값이 아니다 (명세 §5).
 */
data class Photo(
    val id: Long,
    val url: String,
)
