package com.yong.travel.photo.storage

import org.springframework.web.multipart.MultipartFile

data class StoredPhoto(
    val storageKey: String,
    val originalFileName: String,
    val contentType: String,
    val fileSizeBytes: Long,
)

/** 파일시스템 구현이 현재 기본값이며, 향후 S3 구현체로 교체한다. */
interface PhotoStorageService {
    fun store(file: MultipartFile): StoredPhoto
    fun delete(storageKey: String)
    fun resolveUrl(storageKey: String): String
}
