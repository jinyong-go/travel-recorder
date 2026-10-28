package com.yong.travel.common.util

/** 앞뒤 공백을 지운 문자열. 비었으면 null 이다. */
fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
