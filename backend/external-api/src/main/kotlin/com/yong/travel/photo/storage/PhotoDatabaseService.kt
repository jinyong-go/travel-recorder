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
 * 사진 바이너리를 DB(photo_data)에 저장·조회하고 접근 URL 을 만든다.
 *
 * 구현이 하나뿐이라 인터페이스를 두지 않는다. 오브젝트 스토리지로 전환할 때 추상화한다.
 * 삭제 메서드가 없는 것은 의도다 — 사진 행을 지우면 ON DELETE CASCADE 가 바이너리를 함께 지운다.
 */
@Service
@Transactional(readOnly = true)
class PhotoDatabaseService(
    private val photoDataRepository: PhotoDataRepository,
) {

    /**
     * 이미 저장된 사진 메타데이터에 바이너리를 붙인다. 업로드 검증은 호출자의 몫이다.
     *
     * @param photo 저장된 사진 메타데이터. id 가 채워져 있어야 한다
     * @param file 업로드된 파일
     */
    @Transactional
    fun store(photo: PhotoEntity, file: MultipartFile) {
        photoDataRepository.save(PhotoDataEntity(photo, file.bytes))
    }

    /**
     * 서빙할 바이너리를 읽는다.
     *
     * ⚠️ 공개 범위를 판정하지 않는다. 추측 불가능한 UUID 에만 의존하는 알려진 한계다.
     *
     * @param photoId 사진 id
     * @return 바이너리와 Content-Type
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없을 때
     */
    fun load(photoId: UUID): PhotoContent =
        photoDataRepository.findContentById(photoId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

    /**
     * 사진의 접근 URL 을 만든다. 클라이언트는 이 값을 그대로 쓰며 id 로 URL 을 조립하지 않는다.
     *
     * @param photoId 사진 id
     * @return 서버 기준 상대 경로 URL
     */
    fun urlOf(photoId: UUID): String = "$PHOTO_URL_PREFIX/$photoId"

    companion object {
        const val PHOTO_URL_PREFIX = "/api/files/photos"
    }
}
