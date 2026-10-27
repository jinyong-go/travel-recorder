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

/**
 * 사진 바이너리 서빙. 비로그인도 부를 수 있으며, 소속 여행을 볼 수 있는 사진만 내려준다.
 * 쿠키 세션이라 `<img>` 요청에도 로그인이 실린다.
 */
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
            // 볼 수 있는지가 바뀔 수 있어 오래 캐시하면 공유를 해제하거나 로그아웃한 뒤에도 같은 브라우저에
            // 남는다. 1시간으로 묶고, 비공개 사진이 공유 캐시(프록시)에 남지 않도록 private 으로 둔다.
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .body(content.data)
    }
}
