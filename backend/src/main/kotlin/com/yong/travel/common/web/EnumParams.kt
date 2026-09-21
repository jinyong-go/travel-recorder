package com.yong.travel.common.web

import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory

/**
 * 쿼리 파라미터의 열거형 값을 명세 표기 그대로 받는다.
 *
 * 명세는 `scope=mine`, `sort=startDate` 처럼 소문자·camelCase 로 규정하지만(§4.1, §4.3.1),
 * Spring 의 기본 변환기는 `Enum.valueOf` 를 그대로 써서 `MINE`, `START_DATE` 만 통과시킨다.
 * 열거형 상수 이름을 명세 표기에 맞추는 대신 입력 쪽에서 흡수한다 — 코틀린 열거형은 대문자
 * SNAKE_CASE 가 관례라, 그쪽을 뒤집으면 코드 전체가 어색해지고 이득도 없다.
 *
 * 알 수 없는 값은 예외를 던져 기존대로 `400 VALIDATION_ERROR` 가 된다 (§6).
 */
object EnumParamConverterFactory : ConverterFactory<String, Enum<*>> {

    override fun <T : Enum<*>> getConverter(targetType: Class<T>): Converter<String, T> =
        Converter { source ->
            val normalized = normalize(source)
            targetType.enumConstants.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException(
                    "${targetType.simpleName} 에 없는 값입니다: $source",
                )
        }

    /** `startDate` → `START_DATE`, `mine` → `MINE`. 이미 대문자면 그대로 둔다. */
    private fun normalize(source: String): String =
        source.trim().replace(CAMEL_BOUNDARY, "$1_$2").uppercase()

    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
}
