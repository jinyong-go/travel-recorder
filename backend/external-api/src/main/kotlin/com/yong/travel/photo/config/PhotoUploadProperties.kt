package com.yong.travel.photo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize

/**
 * 사진 업로드 제한. 환경변수로 덮어쓸 수 있으며
 * spring.servlet.multipart 설정 및 프론트엔드 VITE_MAX_PHOTO_* 값과 같은 값을 유지해야 한다.
 */
@Component
@ConfigurationProperties(prefix = "app.upload")
class PhotoUploadProperties {
    /** 사진 1장당 최대 용량 */
    var maxPhotoSize: DataSize = DataSize.ofMegabytes(5)

    /** 허용 이미지 MIME 타입 */
    var allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp")
}
