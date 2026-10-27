package com.yong.travel.photo.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.web.requireLogin
import com.yong.travel.photo.service.PhotoService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** 기록에 딸린 사진의 업로드·삭제 API. 바이너리 서빙은 [PhotoFileController] 의 몫이다. */
@RestController
@RequestMapping("/api/records/{recordId}/photos")
class PhotoController(
    private val photoService: PhotoService,
) {

    /**
     * 기록에 사진을 여러 장 올린다. 기록 작성자만 할 수 있다.
     *
     * @param files 올릴 이미지 파일들 (`files` 파트)
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable recordId: Long,
        @RequestParam("files") files: List<MultipartFile>,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<List<PhotoResponse>> {
        val photos = photoService.upload(recordId, requireLogin(principal), files)
        return ResponseEntity.status(HttpStatus.CREATED).body(photos.map { PhotoResponse.from(it) })
    }

    /** 사진을 삭제한다. 바이너리까지 함께 지우는 물리 삭제다. */
    @DeleteMapping("/{photoId}")
    fun delete(
        @PathVariable recordId: Long,
        @PathVariable photoId: UUID,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        photoService.delete(recordId, photoId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
