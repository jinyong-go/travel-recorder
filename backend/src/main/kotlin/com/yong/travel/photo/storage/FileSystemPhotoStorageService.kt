package com.yong.travel.photo.storage

import com.yong.travel.photo.config.StorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "filesystem", matchIfMissing = true)
class FileSystemPhotoStorageService(
    private val storageProperties: StorageProperties,
) : PhotoStorageService {

    override fun store(file: MultipartFile): StoredPhoto {
        val today = LocalDate.now()
        val relativeDir = "%04d/%02d/%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val extension = file.originalFilename?.substringAfterLast('.', "").orEmpty()
        val fileName = if (extension.isBlank()) "${UUID.randomUUID()}" else "${UUID.randomUUID()}.$extension"

        val targetDir = Path.of(storageProperties.local.rootDir, relativeDir)
        Files.createDirectories(targetDir)
        file.transferTo(targetDir.resolve(fileName))

        return StoredPhoto(
            storageKey = "$relativeDir/$fileName",
            originalFileName = file.originalFilename ?: fileName,
            contentType = file.contentType ?: "application/octet-stream",
            fileSizeBytes = file.size,
        )
    }

    override fun delete(storageKey: String) {
        Files.deleteIfExists(Path.of(storageProperties.local.rootDir, storageKey))
    }

    override fun resolveUrl(storageKey: String): String = "$PHOTO_URL_PREFIX/$storageKey"

    companion object {
        const val PHOTO_URL_PREFIX = "/api/files/photos"
    }
}
