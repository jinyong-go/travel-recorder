package com.yong.travel.common.util

/** LIKE 이스케이프 문자. */
const val LIKE_ESCAPE = '\\'

/**
 * 부분 일치 LIKE 패턴을 만든다. 검색어의 `%`·`_`·`\` 는 글자 그대로 찾도록 이스케이프한다.
 * 이스케이프 문자를 가장 먼저 바꿔야 두 번 이스케이프되지 않는다.
 */
fun containsPattern(keyword: String): String =
    "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
