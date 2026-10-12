package com.yong.travel.search.presentation

import com.yong.travel.search.domain.PlaceCandidate

/** 장소 검색 후보 한 건. 기록 등록 폼을 채우는 데 쓴다. */
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
        /**
         * 장소 후보 → 응답.
         *
         * @param candidate 장소 검색 후보
         * @return 응답 DTO
         */
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
