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
) {
    companion object {
        fun from(candidate: PlaceCandidate) = PlaceSearchResultResponse(
            name = candidate.name,
            category = candidate.category,
            address = candidate.address,
            roadAddress = candidate.roadAddress,
            telephone = candidate.telephone,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            distanceKm = candidate.distanceKm,
            link = candidate.link,
        )
    }
}
