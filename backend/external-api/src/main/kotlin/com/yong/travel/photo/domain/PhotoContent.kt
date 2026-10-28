package com.yong.travel.photo.domain

/**
 * 사진 바이너리와 형식.
 * 배열 필드는 data class 의 equals 가 참조를 비교하므로 일반 클래스로 둔다.
 */
class PhotoContent(
    val contentType: String,
    val data: ByteArray,
)
