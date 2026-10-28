package com.yong.travel.photo.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.photo.service.PhotoService
import com.yong.travel.photo.storage.PhotoDatabaseService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 사진 파일 API. 볼 수 있는 여행의 사진만 내려 준다. */
@RestController
class PhotoFileController(
    private val photoService: PhotoService,
) {

    /** 요청자가 볼 수 있는 사진의 바이너리를 내려준다. */
    @GetMapping("${PhotoDatabaseService.PHOTO_URL_PREFIX}/{photoId}")
    fun serve(
        @PathVariable photoId: UUID,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<ByteArray> {
        val content = photoService.load(photoId, principal?.userId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType))
            // 공개 범위가 바뀔 수 있어 캐시는 1시간으로 짧게, 프록시에 남지 않게 private 으로 둔다.
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .body(content.data)
    }
}
