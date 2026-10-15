package com.yong.travel.common.util

/**
 * 앞뒤 공백을 지운 문자열. 지우고 나서 비었거나 원래 null 이면 null 이다.
 *
 * 공백만 입력된 검색어·메모를 "입력 없음" 으로 다루는 자리에서 쓴다.
 */
fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
