package com.yong.travel.photo.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.web.requireLogin
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
     * @param recordId 사진을 붙일 기록 id
     * @param files 올릴 이미지 파일들 (`files` 파트)
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 저장된 사진 목록
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     * @throws ApiException `INVALID_FILE` — 허용되지 않는 형식·용량·파일일 때
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable recordId: Long,
        @RequestParam("files") files: List<MultipartFile>,
        @AuthenticationPrincipal principal: LoginUser?,
    ): List<PhotoResponse> =
        photoService.upload(recordId, requireLogin(principal), files).map { PhotoResponse.from(it) }

    /**
     * 사진을 삭제한다. 바이너리까지 함께 지우는 물리 삭제다.
     *
     * @param recordId 사진이 달린 기록 id
     * @param photoId 지울 사진 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 본문 없는 `204`
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 그 기록의 사진이 아닐 때
     */
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
