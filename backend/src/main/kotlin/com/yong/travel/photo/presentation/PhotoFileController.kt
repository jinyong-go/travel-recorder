package com.yong.travel.photo.presentation

import com.yong.travel.photo.storage.PhotoDatabaseService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 사진 바이너리 서빙.
 *
 * ⚠️ 로그인도 공개 범위도 요구하지 않는다. URL 을 아는 사람은 비공개 여행의 사진도 볼 수 있으며,
 * 추측 불가능한 UUID 에만 의존한다 (명세 §4.6, §8.2).
 */
@RestController
class PhotoFileController(
    private val photoDatabaseService: PhotoDatabaseService,
) {

    @GetMapping("${PhotoDatabaseService.PHOTO_URL_PREFIX}/{photoId}")
    fun serve(@PathVariable photoId: UUID): ResponseEntity<ByteArray> {
        val content = photoDatabaseService.load(photoId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType))
            // 수정 API 가 없어 한 번 올라간 사진은 바뀌지 않는다. 비공개 여행의 사진이 공유 캐시(프록시)에
            // 남지 않도록 private 으로 둔다.
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
            .body(content.data)
    }
}
