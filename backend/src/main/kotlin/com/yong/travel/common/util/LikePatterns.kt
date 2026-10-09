package com.yong.travel.common.util

/** LIKE 의 이스케이프 문자. 쿼리의 `escape` 절과 [containsPattern] 이 같은 값을 써야 한다. */
const val LIKE_ESCAPE = '\\'

/**
 * 부분 일치 LIKE 패턴. 검색어의 `%`·`_`·`\` 를 글자 그대로 찾도록 이스케이프한다.
 *
 * 그대로 두면 `%` 하나로 전체가, `_` 로 임의의 한 글자가 걸린다. 이스케이프 문자 자신을 가장 먼저
 * 바꿔야 뒤에서 붙인 이스케이프가 다시 이스케이프되지 않는다.
 */
fun containsPattern(keyword: String): String =
    "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
