package com.yong.travel.photo.domain

/**
 * 서빙할 사진 한 장의 바이너리와 그 형식.
 *
 * data class 로 만들지 않는다. 배열 필드는 생성되는 equals/hashCode 가 내용이 아니라 참조를 비교한다.
 */
class PhotoContent(
    val contentType: String,
    val data: ByteArray,
)
