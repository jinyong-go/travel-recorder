package com.yong.travel.common.error

/** 모든 실패 응답의 본문. 프론트엔드는 `code` 로 분기한다. */
data class ErrorResponse(
    val code: String,
    val message: String,
    val status: Int,
)
