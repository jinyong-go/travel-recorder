package com.yong.travel.photo.storage

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.domain.PhotoContent
import com.yong.travel.photo.persistence.PhotoDataEntity
import com.yong.travel.photo.persistence.PhotoDataRepository
import com.yong.travel.photo.persistence.PhotoEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * 사진 바이너리를 DB(photo_data)에 저장·조회하고 접근 URL 을 만든다 (명세 §5.1).
 *
 * 구현이 하나뿐이라 인터페이스를 두지 않는다. 오브젝트 스토리지로 전환할 때 추상화한다 (명세 §5.2).
 * 삭제 메서드가 없는 것은 의도다 — 사진 행을 지우면 ON DELETE CASCADE 가 바이너리를 함께 지운다.
 */
@Service
@Transactional(readOnly = true)
class PhotoDatabaseService(
    private val photoDataRepository: PhotoDataRepository,
) {

    /** 이미 저장된 사진 메타데이터에 바이너리를 붙인다. 업로드 검증은 호출자의 몫이다. */
    @Transactional
    fun store(photo: PhotoEntity, file: MultipartFile) {
        photoDataRepository.save(PhotoDataEntity(photo, file.bytes))
    }

    /**
     * 서빙할 바이너리를 읽는다. 없으면 PHOTO_NOT_FOUND 다.
     *
     * ⚠️ 공개 범위를 판정하지 않는다. 추측 불가능한 UUID 에만 의존하는 알려진 한계다 (명세 §4.6, §8.2).
     */
    fun load(photoId: UUID): PhotoContent =
        photoDataRepository.findContentById(photoId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

    fun urlOf(photoId: UUID): String = "$PHOTO_URL_PREFIX/$photoId"

    companion object {
        const val PHOTO_URL_PREFIX = "/api/files/photos"
    }
}
