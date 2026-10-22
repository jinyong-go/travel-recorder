package com.yong.travel.photo.service

import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.domain.PhotoContent
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.trip.record.service.TripRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * 기록에 딸린 사진의 업로드와 삭제. 사진은 기록에 종속되며, 올리고 지울 수 있는 사람은 소속
 * 여행의 소유자뿐이다.
 */
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
     * 사진 바이너리를 읽는다.
     *
     * 로그인도 공개 범위도 확인하지 않는다. 추측할 수 없는 UUID 에만 의존하며, 이것은 알려진 한계다.
     * 판정을 붙이려면 이 메서드에 붙인다 — 그래서 저장소를 컨트롤러가 직접 부르지 않고 여기를 거친다.
     *
     * @param photoId 사진 id
     * @return 사진 바이트와 그 Content-Type
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없을 때
     */
    fun load(photoId: UUID): PhotoContent = photoDatabaseService.load(photoId)

    /**
     * 기록에 사진 여러 장을 올린다.
     *
     * 검증은 파일마다 저장 직전에 하며, 한 장이라도 실패하면 트랜잭션이 통째로 되돌아간다.
     *
     * @param recordId 사진을 붙일 기록 id
     * @param requesterId 요청자의 사용자 id. 소속 여행의 소유자여야 한다
     * @param files 올릴 이미지 파일들
     * @return 저장된 사진들. 올린 순서 그대로다
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때 (존재 은닉)
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     * @throws ApiException `INVALID_FILE` — 형식·용량·파일 시그니처 중 하나라도 맞지 않을 때
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
        // 원본 파일명과 바이너리는 남기지 않는다. 건수와 크기면 업로드 추적에 충분하다.
        log.debug(
            "사진 업로드 recordId={} requesterId={} count={} totalBytes={}",
            recordId, requesterId, files.size, files.sumOf { it.size },
        )
        return photos
    }

    /**
     * 사진을 지운다. 그 사진이 여행 커버였다면 지정도 푼다.
     *
     * @param recordId 사진이 달린 기록 id
     * @param photoId 지울 사진 id
     * @param requesterId 요청자의 사용자 id. 소속 여행의 소유자여야 한다
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때 (존재 은닉)
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 그 기록의 사진이 아닐 때
     */
    @Transactional
    fun delete(recordId: Long, photoId: UUID, requesterId: Long) {
        // 기록을 먼저 읽어 지워진 기록의 사진은 404 로 막는다. 사진 행은 기록이 soft delete 돼도 남아 있다.
        val record = recordService.findEditableRecord(recordId, requesterId)
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

    /** 바이트 배열이 주어진 접두 바이트로 시작하는지. */
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
