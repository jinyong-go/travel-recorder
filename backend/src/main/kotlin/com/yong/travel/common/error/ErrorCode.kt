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

    /** 없는 초대와 "당사자가 아닌 초대" 가 같은 코드·같은 문구를 쓴다. 보낸 소유자와 받은 사람 외에는 존재도 드러나지 않아야 한다. */
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 초대입니다."),

    /** 초대 대상 이메일의 가입자가 없는 경우. 가입 여부를 숨기지 않는 것은 의도된 선택이다 (공통 명세 §3.7, §7.2). */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "가입되지 않은 이메일입니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이거나 다른 요청과 충돌했습니다."),
    GROUP_MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "그룹 정원이 가득 찼습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 그룹 멤버입니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색을 사용할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
