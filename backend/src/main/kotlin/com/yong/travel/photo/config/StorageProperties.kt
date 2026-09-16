package com.yong.travel.photo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.storage")
class StorageProperties {
    /** filesystem | s3 (s3 는 후속 과제) */
    var type: String = "filesystem"
    var local: Local = Local()

    class Local {
        var rootDir: String = "./uploads"
    }
}
