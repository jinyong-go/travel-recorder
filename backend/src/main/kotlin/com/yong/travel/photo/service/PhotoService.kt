package com.yong.travel.photo.service

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.domain.VisitRecord
import com.yong.travel.record.repository.VisitRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

interface PhotoService {
    fun upload(recordId: Long, requesterId: Long, files: List<MultipartFile>): List<PhotoResponse>

    fun delete(recordId: Long, photoId: Long, requesterId: Long)
}

/**
 * 사진은 기록에 종속되며, 올리고 지울 수 있는 사람은 기록 작성자뿐이다.
 * 기록이 개인 데이터가 되면서 "업로더 또는 등록자" 라는 구분 자체가 사라졌다.
 */
@Service
@Transactional(readOnly = true)
class PhotoServiceImpl(
    private val recordRepository: VisitRecordRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val uploadProperties: PhotoUploadProperties,
) : PhotoService {

    @Transactional
    override fun upload(recordId: Long, requesterId: Long, files: List<MultipartFile>): List<PhotoResponse> {
        val record = findOwnRecord(recordId, requesterId)

        return files.map { file ->
            if (file.contentType !in uploadProperties.allowedContentTypes ||
                file.size > uploadProperties.maxPhotoSize.toBytes()
            ) {
                throw ApiException(ErrorCode.INVALID_FILE)
            }
            val stored = photoStorageService.store(file)
            val photo = photoRepository.save(
                Photo(
                    record = record,
                    storageKey = stored.storageKey,
                    originalFileName = stored.originalFileName,
                    contentType = stored.contentType,
                    fileSizeBytes = stored.fileSizeBytes,
                ),
            )
            PhotoResponse(requireNotNull(photo.id), photoStorageService.resolveUrl(photo.storageKey))
        }
    }

    @Transactional
    override fun delete(recordId: Long, photoId: Long, requesterId: Long) {
        findOwnRecord(recordId, requesterId)
        val photo = photoRepository.findByIdAndRecordId(photoId, recordId)
            ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

        photoStorageService.delete(photo.storageKey)
        photoRepository.delete(photo)
    }

    /**
     * 기록을 먼저 조회해 soft delete 된 기록의 사진은 404 로 막는다.
     * (Photo 는 soft delete 대상이 아니라 삭제된 기록의 사진 행이 그대로 남아 있다.)
     *
     * 남의 기록이면 403 이 아니라 404 다 — 작성자가 아닌 사람에게는 그 기록의 존재 자체를 알리지 않는다.
     */
    private fun findOwnRecord(recordId: Long, requesterId: Long): VisitRecord {
        val record = recordRepository.findById(recordId)
            .orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }
        if (record.author.id != requesterId) throw ApiException(ErrorCode.RECORD_NOT_FOUND)
        return record
    }
}
