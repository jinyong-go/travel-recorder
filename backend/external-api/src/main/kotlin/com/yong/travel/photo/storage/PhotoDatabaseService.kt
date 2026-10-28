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
 * 사진 바이너리를 DB 에 저장·조회하고 접근 URL 을 만든다.
 * 구현이 하나뿐이라 인터페이스는 두지 않는다.
 */
@Service
@Transactional(readOnly = true)
class PhotoDatabaseService(
    private val photoDataRepository: PhotoDataRepository,
) {

    /** 저장된 사진에 바이너리를 붙인다. 파일 검증은 하지 않는다. */
    @Transactional
    fun store(photo: PhotoEntity, file: MultipartFile) {
        photoDataRepository.save(PhotoDataEntity(photo, file.bytes))
    }

    /**
     * 사진 바이너리를 읽는다. 공개 범위는 확인하지 않는다.
     *
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없을 때
     */
    fun load(photoId: UUID): PhotoContent =
        photoDataRepository.findContentById(photoId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

    /** 사진 접근 URL 을 만든다. */
    fun urlOf(photoId: UUID): String = "$PHOTO_URL_PREFIX/$photoId"

    /** 기록에 달린 사진의 바이너리를 지운다. 메타데이터는 남는다. */
    @Transactional
    fun deleteByRecord(recordId: Long): Int = photoDataRepository.deleteByRecordId(recordId)

    /** 여행의 모든 기록에 달린 사진의 바이너리를 지운다. 메타데이터는 남는다. */
    @Transactional
    fun deleteByTrip(tripId: Long): Int = photoDataRepository.deleteByTripId(tripId)

    companion object {
        const val PHOTO_URL_PREFIX = "/api/files/photos"
    }
}
