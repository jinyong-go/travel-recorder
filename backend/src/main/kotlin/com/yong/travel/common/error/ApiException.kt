package com.yong.travel.common.error

class ApiException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
) : RuntimeException(message)
