package com.yong.travel.common.config

import com.yong.travel.photo.storage.FileSystemPhotoStorageService
import com.yong.travel.photo.config.StorageProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class WebConfig(
    private val storageProperties: StorageProperties,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val rootDir = Path.of(storageProperties.local.rootDir).toAbsolutePath()
        registry.addResourceHandler("${FileSystemPhotoStorageService.PHOTO_URL_PREFIX}/**")
            .addResourceLocations("file:$rootDir/")
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowCredentials(true)
    }
}
