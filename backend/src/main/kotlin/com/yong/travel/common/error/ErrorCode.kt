package com.yong.travel.common.error

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    /**
     * 없는 기록과 "볼 권한이 없는 기록" 이 같은 코드·같은 문구를 쓴다.
     * 403 으로 나누거나 문구를 달리하면 그 차이만으로 비공개 기록의 존재가 드러난다.
     */
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 기록입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 그룹입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사진입니다."),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이거나 다른 요청과 충돌했습니다."),
    GROUP_MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "그룹 정원이 가득 찼습니다."),

    /** 없는 토큰(404)과 구분한다. 만료는 "재발급을 요청하세요" 로 안내할 수 있는 상황이기 때문이다. */
    INVITE_EXPIRED(HttpStatus.GONE, "초대 링크가 만료되었습니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색을 사용할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
