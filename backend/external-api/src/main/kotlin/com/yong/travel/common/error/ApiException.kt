package com.yong.travel.common.error

/** 도메인 실패를 알리는 예외. `GlobalExceptionHandler` 가 [errorCode] 의 상태와 메시지로 응답한다. */
class ApiException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
) : RuntimeException(message)
