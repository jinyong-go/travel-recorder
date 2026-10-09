package com.yong.travel.photo.service

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.record.persistence.TripRecordEntity
import com.yong.travel.record.persistence.TripRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * 사진은 기록에 종속되며, 올리고 지울 수 있는 사람은 기록 작성자뿐이다.
 * 기록이 개인 데이터가 되면서 "업로더 또는 등록자" 라는 구분 자체가 사라졌다.
 */
@Service
@Transactional(readOnly = true)
class PhotoService(
    private val recordRepository: TripRecordRepository,
    private val photoRepository: PhotoRepository,
    private val photoDatabaseService: PhotoDatabaseService,
    private val uploadProperties: PhotoUploadProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(recordId: Long, requesterId: Long, files: List<MultipartFile>): List<Photo> {
        val record = findOwnRecord(recordId, requesterId)
        // 원본 파일명과 바이너리는 남기지 않는다. 건수와 크기면 업로드 추적에 충분하다.
        log.debug(
            "사진 업로드 recordId={} requesterId={} 건수={} 총크기={}bytes",
            recordId, requesterId, files.size, files.sumOf { it.size },
        )

        return files.map { file ->
            if (file.contentType !in uploadProperties.allowedContentTypes ||
                file.size > uploadProperties.maxPhotoSize.toBytes() ||
                !hasSignatureOf(file, requireNotNull(file.contentType))
            ) {
                throw ApiException(ErrorCode.INVALID_FILE)
            }
            val photo = photoRepository.save(
                PhotoEntity(
                    record = record,
                    // 표시용 값이라 길면 잘라 저장한다. 업로드를 거부할 이유가 없다.
                    originalFileName = file.originalFilename.orEmpty().take(MAX_FILE_NAME_LENGTH),
                    contentType = requireNotNull(file.contentType),
                    fileSizeBytes = file.size,
                ),
            )
            photoDatabaseService.store(photo, file)
            val id = requireNotNull(photo.id)
            Photo(id, photoDatabaseService.urlOf(id))
        }
    }

    @Transactional
    fun delete(recordId: Long, photoId: UUID, requesterId: Long) {
        val record = findOwnRecord(recordId, requesterId)
        val photo = photoRepository.findByIdAndRecordId(photoId, recordId)
            ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

        // 외래키가 없어 ON DELETE SET NULL 이 돌지 않는다. 여기서 직접 풀지 않으면
        // 없는 사진을 가리키는 커버가 남아 여행 조회가 깨진다.
        record.trip.clearCoverIfAmong(listOf(photoId))

        // 바이너리(photo_data)는 ON DELETE CASCADE 로 함께 지워진다.
        photoRepository.delete(photo)
        log.debug("사진 삭제 recordId={} photoId={} requesterId={}", recordId, photoId, requesterId)
    }

    /**
     * 기록을 먼저 조회해 soft delete 된 기록의 사진은 404 로 막는다.
     * (PhotoEntity 는 soft delete 대상이 아니라 삭제된 기록의 사진 행이 그대로 남아 있다.)
     *
     * 남의 기록이면 403 이 아니라 404 다 — 작성자가 아닌 사람에게는 그 기록의 존재 자체를 알리지 않는다.
     */
    private fun findOwnRecord(recordId: Long, requesterId: Long): TripRecordEntity {
        val record = recordRepository.findById(recordId)
            .orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }
        if (record.trip.owner.id != requesterId) {
            // 존재 은닉 때문에 응답이 "없는 기록"과 같다.
            log.debug(
                "사진 접근 차단 recordId={} requesterId={} ownerId={}",
                recordId, requesterId, record.trip.owner.id,
            )
            throw ApiException(ErrorCode.RECORD_NOT_FOUND)
        }
        return record
    }

    /**
     * 파일 앞부분이 선언된 형식의 시그니처와 맞는지 본다.
     *
     * Content-Type 은 클라이언트가 적어 보내는 값이라 그대로 믿지 않는다. 시그니처를 아는 형식은
     * 아래 셋뿐이며, 허용 형식 설정에 다른 형식을 더해도 여기에 추가하기 전까지는 거부된다.
     */
    private fun hasSignatureOf(file: MultipartFile, contentType: String): Boolean {
        val head = file.inputStream.use { it.readNBytes(12) }
        return when (contentType) {
            "image/jpeg" -> head.startsWith(JPEG)
            "image/png" -> head.startsWith(PNG)
            // RIFF 컨테이너라 앞 4바이트(RIFF)와 8~11바이트(WEBP)를 함께 본다.
            "image/webp" -> head.startsWith(RIFF) && head.size >= 12 && head.copyOfRange(8, 12).contentEquals(WEBP)
            else -> false
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private companion object {
        /** photos.original_file_name 컬럼 길이. */
        const val MAX_FILE_NAME_LENGTH = 255

        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val RIFF = "RIFF".toByteArray()
        val WEBP = "WEBP".toByteArray()
    }
}
