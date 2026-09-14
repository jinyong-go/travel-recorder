package com.yong.travel.place.dto

import com.yong.travel.place.domain.Category

enum class PlaceSort {
    RECENT,
    RATING,
    DISTANCE,
}

data class PlaceListQuery(
    val category: Category? = null,
    val tag: String? = null,
    val keyword: String? = null,
    val sort: PlaceSort = PlaceSort.RECENT,
    val lat: Double? = null,
    val lng: Double? = null,
)
