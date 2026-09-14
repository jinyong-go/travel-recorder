package com.yong.travel.common.error

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 여행지입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사진입니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일입니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색을 사용할 수 없습니다."),
}
