package com.yong.travel.photo.controller

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.web.requireLogin
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.dto.toResponse
import com.yong.travel.photo.service.PhotoService
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

@RestController
@RequestMapping("/api/records/{recordId}/photos")
class PhotoController(
    private val photoService: PhotoService,
) {

    /** 기록에 사진 업로드 (여러 장). 기록 작성자만 할 수 있다. */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable recordId: Long,
        @RequestParam("files") files: List<MultipartFile>,
        @AuthenticationPrincipal principal: LoginUser?,
    ): List<PhotoResponse> =
        photoService.upload(recordId, requireLogin(principal), files).map { it.toResponse() }

    /** 사진 삭제. 파일 본체까지 함께 지우는 물리 삭제다. */
    @DeleteMapping("/{photoId}")
    fun delete(
        @PathVariable recordId: Long,
        @PathVariable photoId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        photoService.delete(recordId, photoId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
