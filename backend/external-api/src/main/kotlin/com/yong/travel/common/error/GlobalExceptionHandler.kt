package com.yong.travel.common.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.ErrorResponseException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** 모든 예외를 같은 오류 응답 형태로 바꾼다. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 애플리케이션이 던진 예외. */
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> =
        toResponse(e.errorCode, e.message)

    /** 요청 본문 검증 실패. 틀린 필드와 이유를 알려 준다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return toResponse(ErrorCode.VALIDATION_ERROR, message)
    }

    /** 파라미터 검증 실패. */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val message = e.constraintViolations
            .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return toResponse(ErrorCode.VALIDATION_ERROR, message)
    }

    /** 경로·쿼리 파라미터의 형식이 틀렸을 때. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.VALIDATION_ERROR, "${e.name}: 값의 형식이 올바르지 않습니다.")

    /** 필수 파라미터나 파트가 빠졌을 때. */
    @ExceptionHandler(MissingServletRequestParameterException::class, MissingServletRequestPartException::class)
    fun handleMissingRequestValue(e: Exception): ResponseEntity<ErrorResponse> {
        val name = when (e) {
            is MissingServletRequestParameterException -> e.parameterName
            is MissingServletRequestPartException -> e.requestPartName
            else -> null
        }
        val message = name?.let { "$it: 필수 값이 누락되었습니다." } ?: ErrorCode.VALIDATION_ERROR.defaultMessage
        return toResponse(ErrorCode.VALIDATION_ERROR, message)
    }

    /** 본문을 읽을 수 없을 때. 내부 구조가 드러나지 않도록 상세는 싣지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.VALIDATION_ERROR, "요청 본문을 읽을 수 없습니다.")

    /** 업로드 용량 초과. 서비스의 용량 검증과 같은 코드로 답한다. */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.INVALID_FILE, "업로드 용량 제한을 초과했습니다.")

    /** 지원하지 않는 메서드. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.METHOD_NOT_ALLOWED)

    /** 지원하지 않는 Content-Type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE)

    /** 없는 주소. */
    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    fun handleNotFound(e: Exception): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.NOT_FOUND)

    /** 컨트롤러 안에서 접근이 거부됐을 때. */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.FORBIDDEN)

    /**
     * DB 제약 위반. 주로 동시 요청이 겹칠 때 생긴다.
     * 원인 메시지에 테이블·제약 이름이 있어 로그로만 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("데이터 무결성 제약 위반", e)
        return toResponse(ErrorCode.CONFLICT)
    }

    /** Spring 이 상태 코드를 담아 던진 예외는 그 상태로 답한다. */
    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponseException(e: ErrorResponseException): ResponseEntity<ErrorResponse> {
        val status = e.statusCode
        val code = genericCodeOf(status.value())
        if (status.is5xxServerError) log.error("처리되지 않은 서버 오류", e)
        return ResponseEntity.status(status).body(ErrorResponse(code.name, code.defaultMessage, status.value()))
    }

    /**
     * 상태 코드에 맞는 범용 오류 코드.
     * 상태로 찾으면 같은 상태의 도메인 코드가 걸릴 수 있어 직접 나열한다.
     */
    private fun genericCodeOf(status: Int): ErrorCode = when (status) {
        401 -> ErrorCode.UNAUTHENTICATED
        403 -> ErrorCode.FORBIDDEN
        404 -> ErrorCode.NOT_FOUND
        405 -> ErrorCode.METHOD_NOT_ALLOWED
        409 -> ErrorCode.CONFLICT
        415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
        in 400..499 -> ErrorCode.BAD_REQUEST
        else -> ErrorCode.INTERNAL_ERROR
    }

    /** 그 밖의 모든 예외. 내부 정보가 새지 않도록 고정 문구로 답한다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", e)
        return toResponse(ErrorCode.INTERNAL_ERROR)
    }

    /** 오류 응답을 만든다. */
    private fun toResponse(
        errorCode: ErrorCode,
        message: String = errorCode.defaultMessage,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(errorCode.status)
            .body(ErrorResponse(errorCode.name, message, errorCode.status.value()))
}
