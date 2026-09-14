package com.yong.travel.common.error

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message, e.errorCode.status.value()))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR.name, message, HttpStatus.BAD_REQUEST.value()))
    }
}
