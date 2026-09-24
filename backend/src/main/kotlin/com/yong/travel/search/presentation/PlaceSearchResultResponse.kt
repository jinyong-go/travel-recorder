package com.yong.travel.search.presentation

import com.yong.travel.search.domain.PlaceCandidate

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

fun PlaceCandidate.toResponse(): PlaceSearchResultResponse =
    PlaceSearchResultResponse(
        name = name,
        category = category,
        address = address,
        roadAddress = roadAddress,
        telephone = telephone,
        latitude = latitude,
        longitude = longitude,
        distanceKm = distanceKm,
        link = link,
    )
