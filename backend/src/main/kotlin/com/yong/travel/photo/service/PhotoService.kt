package com.yong.travel.photo.service

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.domain.TripRecord
import com.yong.travel.record.repository.TripRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * 사진은 기록에 종속되며, 올리고 지울 수 있는 사람은 기록 작성자뿐이다.
 * 기록이 개인 데이터가 되면서 "업로더 또는 등록자" 라는 구분 자체가 사라졌다.
 */
@Service
@Transactional(readOnly = true)
class PhotoService(
    private val recordRepository: TripRecordRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val uploadProperties: PhotoUploadProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(recordId: Long, requesterId: Long, files: List<MultipartFile>): List<PhotoResponse> {
        val record = findOwnRecord(recordId, requesterId)
        // 원본 파일명과 바이너리는 남기지 않는다. 건수와 크기면 업로드 추적에 충분하다.
        log.debug(
            "사진 업로드 recordId={} requesterId={} 건수={} 총크기={}bytes",
            recordId, requesterId, files.size, files.sumOf { it.size },
        )

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
    fun delete(recordId: Long, photoId: Long, requesterId: Long) {
        val record = findOwnRecord(recordId, requesterId)
        val photo = photoRepository.findByIdAndRecordId(photoId, recordId)
            ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

        // 외래키가 없어 ON DELETE SET NULL 이 돌지 않는다. 여기서 직접 풀지 않으면
        // 없는 사진을 가리키는 커버가 남아 여행 조회가 깨진다 (명세 §3, §4.6).
        record.trip.clearCoverIfAmong(listOf(photoId))

        photoStorageService.delete(photo.storageKey)
        photoRepository.delete(photo)
        log.debug("사진 삭제 recordId={} photoId={} requesterId={}", recordId, photoId, requesterId)
    }

    /**
     * 기록을 먼저 조회해 soft delete 된 기록의 사진은 404 로 막는다.
     * (Photo 는 soft delete 대상이 아니라 삭제된 기록의 사진 행이 그대로 남아 있다.)
     *
     * 남의 기록이면 403 이 아니라 404 다 — 작성자가 아닌 사람에게는 그 기록의 존재 자체를 알리지 않는다.
     */
    private fun findOwnRecord(recordId: Long, requesterId: Long): TripRecord {
        val record = recordRepository.findById(recordId)
            .orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }
        if (record.trip.owner.id != requesterId) {
            // 존재 은닉 때문에 응답이 "없는 기록"과 같다 (명세 §2.2).
            log.debug(
                "사진 접근 차단 recordId={} requesterId={} ownerId={}",
                recordId, requesterId, record.trip.owner.id,
            )
            throw ApiException(ErrorCode.RECORD_NOT_FOUND)
        }
        return record
    }
}
