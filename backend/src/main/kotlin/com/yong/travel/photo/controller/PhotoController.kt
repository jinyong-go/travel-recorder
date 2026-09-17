package com.yong.travel.photo.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.web.requireLogin
import com.yong.travel.photo.dto.PhotoResponse
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

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable recordId: Long,
        @RequestParam("files") files: List<MultipartFile>,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): List<PhotoResponse> = photoService.upload(recordId, requireLogin(principal), files)

    @DeleteMapping("/{photoId}")
    fun delete(
        @PathVariable recordId: Long,
        @PathVariable photoId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        photoService.delete(recordId, photoId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
