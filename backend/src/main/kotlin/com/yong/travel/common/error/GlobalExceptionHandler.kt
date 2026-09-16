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

/**
 * 모든 예외를 `ErrorResponse` 한 가지 형태로 변환한다.
 * 어떤 경로로 실패하든 프론트엔드가 `code` 로 분기할 수 있도록, 마지막에 `Exception` 캐치올을 둔다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 애플리케이션이 의도적으로 던진 예외. 상태 코드와 메시지를 그대로 쓴다. */
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> =
        toResponse(e.errorCode, e.message)

    /** `@Valid` 로 검증한 요청 본문. 어떤 필드가 왜 틀렸는지 그대로 알려준다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return toResponse(ErrorCode.VALIDATION_ERROR, message)
    }

    /** `@Validated` 가 붙은 파라미터 검증 실패. */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val message = e.constraintViolations
            .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return toResponse(ErrorCode.VALIDATION_ERROR, message)
    }

    /** 경로·쿼리 파라미터의 타입이 맞지 않는 경우 (예: `/api/places/abc`). */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.VALIDATION_ERROR, "${e.name}: 값의 형식이 올바르지 않습니다.")

    /** 필수 파라미터/파트 누락. */
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

    /** 본문이 없거나 JSON 으로 읽을 수 없는 경우. 파싱 실패 상세는 내부 구조가 드러나므로 싣지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.VALIDATION_ERROR, "요청 본문을 읽을 수 없습니다.")

    /**
     * 멀티파트 단계에서 걸린 업로드 용량 초과.
     * `PhotoService` 의 자체 용량 검증과 같은 사용자 실수이므로 상태 코드도 `INVALID_FILE`(400) 로 맞춘다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.INVALID_FILE, "업로드 용량 제한을 초과했습니다.")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.METHOD_NOT_ALLOWED)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE)

    /** 매핑되지 않은 주소, 존재하지 않는 정적 리소스(업로드된 사진 파일 포함). */
    @ExceptionHandler(NoHandlerFoundException::class, NoResourceFoundException::class)
    fun handleNotFound(e: Exception): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.NOT_FOUND)

    /** 메서드 시큐리티를 켠 뒤 컨트롤러 안에서 거부된 경우. 필터 단계의 거부는 시큐리티가 직접 처리한다. */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        toResponse(ErrorCode.FORBIDDEN)

    /**
     * 유니크·체크·외래키 제약 위반. 동시 요청 경합(같은 사용자의 리뷰 중복 등록 등)이 대표적이다.
     * 원인 메시지에 테이블·제약 이름이 들어 있어 그대로 노출하지 않고 로그로만 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("데이터 무결성 제약 위반", e)
        return toResponse(ErrorCode.CONFLICT)
    }

    /** Spring 이 상태 코드를 담아 던지는 예외는 그 상태를 존중한다 (캐치올이 500 으로 뭉개지 않도록). */
    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponseException(e: ErrorResponseException): ResponseEntity<ErrorResponse> {
        val status = e.statusCode
        val code = ErrorCode.entries.find { it.status.value() == status.value() } ?: ErrorCode.INTERNAL_ERROR
        if (status.is5xxServerError) log.error("처리되지 않은 서버 오류", e)
        return ResponseEntity.status(status).body(ErrorResponse(code.name, code.defaultMessage, status.value()))
    }

    /** 위에서 걸리지 않은 모든 예외. 내부 정보가 새지 않도록 메시지는 고정하고 스택은 로그로 남긴다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", e)
        return toResponse(ErrorCode.INTERNAL_ERROR)
    }

    private fun toResponse(
        errorCode: ErrorCode,
        message: String = errorCode.defaultMessage,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(errorCode.status)
            .body(ErrorResponse(errorCode.name, message, errorCode.status.value()))
}
