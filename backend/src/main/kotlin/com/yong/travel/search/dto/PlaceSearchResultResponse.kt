package com.yong.travel.search.dto

data class PlaceSearchResultResponse(
    val name: String,
    val category: String?,
    val address: String,
    val roadAddress: String?,
    val telephone: String?,
    val latitude: Double,
    val longitude: Double,
    /** 기준 위치(lat/lng)가 전달된 경우에만 채워진다. */
    val distanceKm: Double?,
    val link: String?,
)
