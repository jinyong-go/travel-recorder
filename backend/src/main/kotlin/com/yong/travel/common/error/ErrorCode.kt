package com.yong.travel.common.error

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 여행지입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사진입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이거나 다른 요청과 충돌했습니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색을 사용할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
