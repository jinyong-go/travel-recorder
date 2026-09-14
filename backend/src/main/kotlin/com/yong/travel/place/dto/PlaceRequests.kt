package com.yong.travel.place.dto

import com.yong.travel.place.domain.Category
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class PlaceCreateRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val category: Category,

    val tags: List<String> = emptyList(),

    @field:NotBlank
    val address: String,

    val roadAddress: String? = null,

    val externalLink: String? = null,

    @field:NotNull
    val latitude: Double,

    @field:NotNull
    val longitude: Double,

    @field:Size(max = 1000)
    val memo: String? = null,
)

data class PlaceUpdateRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val category: Category,

    val tags: List<String> = emptyList(),

    @field:NotBlank
    val address: String,

    val roadAddress: String? = null,

    val externalLink: String? = null,

    @field:NotNull
    val latitude: Double,

    @field:NotNull
    val longitude: Double,

    @field:Size(max = 1000)
    val memo: String? = null,
)
