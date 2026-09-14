package com.yong.travel.common.error

data class ErrorResponse(
    val code: String,
    val message: String,
    val status: Int,
)
