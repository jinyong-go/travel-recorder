package com.yong.travel.photo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize

/** 사진 업로드 제한. 멀티파트 설정과 프론트엔드 제한값과 맞춰야 한다. */
@Component
@ConfigurationProperties(prefix = "app.upload")
class PhotoUploadProperties {
    /** 사진 1장당 최대 용량 */
    var maxPhotoSize: DataSize = DataSize.ofMegabytes(5)

    /** 허용 이미지 형식 */
    var allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp")
}
