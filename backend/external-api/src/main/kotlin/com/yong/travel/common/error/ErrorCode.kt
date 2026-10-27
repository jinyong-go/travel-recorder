package com.yong.travel.common.error

import org.springframework.http.HttpStatus

/** 실패 상황별 HTTP 상태와 기본 문구. 새 항목을 더하면 명세의 오류 표에도 함께 적는다. */
enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    /**
     * 없는 기록과 "볼 권한이 없는 기록" 이 같은 코드·같은 문구를 쓴다.
     * 403 으로 나누거나 문구를 달리하면 그 차이만으로 비공개 기록의 존재가 드러난다.
     */
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 여행입니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 기록입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사진입니다."),

    /**
     * 그룹은 존재를 숨기지 않는다. 멤버가 아니라서 막힌 경우는 `FORBIDDEN` 이고 이 코드는
     * 그룹이 실제로 없을 때만 쓴다. 단 여행 공유 요청에 담긴 접근 불가 그룹 id 는
     * 예외로 이 코드다 — 그 경로는 남의 그룹 id 를 훑을 수 있다.
     */
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 그룹입니다."),

    /**
     * 그룹에 속하지 않은 사용자를 제외하려 할 때. 그룹은 존재를 숨기지 않으므로 그룹이 없는 경우
     * (`GROUP_NOT_FOUND`)와 구분해 알린다.
     */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹 멤버가 아닙니다."),

    /** 없는 초대와 "당사자가 아닌 초대" 가 같은 코드·같은 문구를 쓴다. 보낸 소유자와 받은 사람 외에는 존재도 드러나지 않아야 한다. */
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 초대입니다."),

    /** 초대 대상 이메일의 가입자가 없는 경우. 가입 여부를 숨기지 않는 것은 의도된 선택이다. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "가입되지 않은 이메일입니다."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    /** 상태만 알려진 요청 실패 중 맞는 범용 코드가 없는 것(406·413·429 등). 응답의 HTTP 상태는 원래 값을 그대로 쓴다. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "처리할 수 없는 요청입니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이거나 다른 요청과 충돌했습니다."),
    GROUP_MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "그룹 정원이 가득 찼습니다."),

    /** 사용자당 그룹 수 상한(소유·참여) 초과. 어느 상한인지는 화면(생성·수락)이 이미 알아 코드를 나누지 않는다. */
    GROUP_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "그룹 수 상한에 도달했습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 그룹 멤버입니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색을 사용할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
