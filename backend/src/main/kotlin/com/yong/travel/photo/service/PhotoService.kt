package com.yong.travel.photo.service

import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.photo.config.PhotoUploadProperties
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.place.repository.PlaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

interface PhotoService {
    fun upload(placeId: Long, uploaderId: Long, files: List<MultipartFile>): List<PhotoResponse>

    /** 업로더 본인 또는 여행지 등록자만 삭제할 수 있다. */
    fun delete(placeId: Long, photoId: Long, requesterId: Long)
}

@Service
@Transactional(readOnly = true)
class PhotoServiceImpl(
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val uploadProperties: PhotoUploadProperties,
) : PhotoService {

    @Transactional
    override fun upload(placeId: Long, uploaderId: Long, files: List<MultipartFile>): List<PhotoResponse> {
        val place = placeRepository.findById(placeId).orElseThrow { ApiException(ErrorCode.PLACE_NOT_FOUND) }
        val uploader = userRepository.findById(uploaderId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }

        return files.map { file ->
            if (file.contentType !in uploadProperties.allowedContentTypes ||
                file.size > uploadProperties.maxPhotoSize.toBytes()
            ) {
                throw ApiException(ErrorCode.INVALID_FILE)
            }
            val stored = photoStorageService.store(file)
            val photo = photoRepository.save(
                Photo(
                    place = place,
                    uploader = uploader,
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
    override fun delete(placeId: Long, photoId: Long, requesterId: Long) {
        val photo = photoRepository.findByIdAndPlaceId(photoId, placeId)
            ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        if (photo.uploader.id != requesterId && photo.place.owner.id != requesterId) {
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        photoStorageService.delete(photo.storageKey)
        photoRepository.delete(photo)
    }
}
