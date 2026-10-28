package com.yong.travel.photo.service

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.domain.PhotoContent
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.persistence.PhotoSpecifications
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.trip.record.service.TripRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** 기록 사진의 업로드·삭제·조회를 맡는다. 올리고 지우는 것은 여행 소유자만 할 수 있다. */
@Service
@Transactional(readOnly = true)
class PhotoService(
    private val recordService: TripRecordService,
    private val photoRepository: PhotoRepository,
    private val photoDatabaseService: PhotoDatabaseService,
    private val uploadProperties: PhotoUploadProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 볼 수 있는 사진의 바이너리를 읽는다.
     *
     * @param userId 비로그인이면 null 이며 전체 공개 여행의 사진만 읽힌다
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 볼 권한이 없을 때
     */
    fun load(photoId: UUID, userId: Long?): PhotoContent {
        if (!photoRepository.exists(PhotoSpecifications.viewableBy(photoId, userId))) {
            log.debug("사진 조회 차단 photoId={} userId={}", photoId, userId)
            throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        }
        return photoDatabaseService.load(photoId)
    }

    /**
     * 기록에 사진 여러 장을 올린다. 한 장이라도 실패하면 모두 되돌린다.
     *
     * @return 올린 순서대로의 사진
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     * @throws ApiException `INVALID_FILE` — 형식·용량·파일 내용이 맞지 않을 때
     */
    @Transactional
    fun upload(recordId: Long, requesterId: Long, files: List<MultipartFile>): List<Photo> {
        val record = recordService.findEditableRecord(recordId, requesterId)

        val photos = files.map { file ->
            if (file.contentType !in uploadProperties.allowedContentTypes ||
                file.size > uploadProperties.maxPhotoSize.toBytes() ||
                !hasSignatureOf(file, requireNotNull(file.contentType))
            ) {
                throw ApiException(ErrorCode.INVALID_FILE)
            }
            val photo = photoRepository.save(
                PhotoEntity(
                    record = record,
                    // 표시용이라 길면 잘라 저장한다.
                    originalFileName = file.originalFilename.orEmpty().take(MAX_FILE_NAME_LENGTH),
                    contentType = requireNotNull(file.contentType),
                    fileSizeBytes = file.size,
                ),
            )
            photoDatabaseService.store(photo, file)
            val id = requireNotNull(photo.id)
            Photo(id, photoDatabaseService.urlOf(id))
        }
        log.debug(
            "사진 업로드 recordId={} requesterId={} count={} totalBytes={}",
            recordId, requesterId, files.size, files.sumOf { it.size },
        )
        return photos
    }

    /**
     * 사진을 지운다. 여행 커버였다면 커버도 해제한다.
     *
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 그 기록의 사진이 아닐 때
     */
    @Transactional
    fun delete(recordId: Long, photoId: UUID, requesterId: Long) {
        // 지워진 기록의 사진도 남아 있으므로 기록을 먼저 확인한다.
        val record = recordService.findEditableRecord(recordId, requesterId)
        val photo = photoRepository.findByIdAndRecordId(photoId, recordId)
            ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)

        // 커버는 외래키가 없어 직접 해제해야 한다.
        record.trip.clearCoverIfAmong(listOf(photoId))

        // 바이너리는 DB 가 함께 지운다.
        photoRepository.delete(photo)
        log.debug("사진 삭제 recordId={} photoId={} requesterId={}", recordId, photoId, requesterId)
    }

    /**
     * 파일 내용이 선언한 형식과 맞는지 확인한다. Content-Type 은 클라이언트가 보낸 값이라 믿지 않는다.
     * 여기 없는 형식은 허용 목록에 있어도 거부된다.
     */
    private fun hasSignatureOf(file: MultipartFile, contentType: String): Boolean {
        val head = file.inputStream.use { it.readNBytes(12) }
        return when (contentType) {
            "image/jpeg" -> head.startsWith(JPEG)
            "image/png" -> head.startsWith(PNG)
            // WebP 는 앞 4바이트(RIFF)와 8~11바이트(WEBP)를 함께 본다.
            "image/webp" -> head.startsWith(RIFF) && head.size >= 12 && head.copyOfRange(8, 12).contentEquals(WEBP)
            else -> false
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private companion object {
        /** 원본 파일명 컬럼 길이. */
        const val MAX_FILE_NAME_LENGTH = 255

        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val RIFF = "RIFF".toByteArray()
        val WEBP = "WEBP".toByteArray()
    }
}
