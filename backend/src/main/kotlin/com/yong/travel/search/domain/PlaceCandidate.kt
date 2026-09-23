package com.yong.travel.search.domain

/**
 * 장소 검색 후보 한 건. 네이버 지역 검색 오픈API 응답을 이 서비스가 쓰는 모양으로 옮긴 것이다.
 *
 * **저장되는 값이 아니다.** 기록 등록 폼을 채우기 위한 조회 결과이며, 사용자가 고른 뒤에야
 * `TripRecord` 의 컬럼으로 옮겨진다 (명세 §4.5).
 *
 * 좌표는 원본 API 의 정수 표기(`mapx`/`mapy`)를 도(度) 단위로 이미 환산한 값이고, 이름에서
 * HTML 태그도 제거되어 있다 — 그 변환은 서비스가 맡는다.
 */
data class PlaceCandidate(
    val name: String,
    val category: String?,
    val address: String,
    val roadAddress: String?,
    val telephone: String?,
    val latitude: Double,
    val longitude: Double,

    /** 요청자의 기준 좌표가 함께 온 경우에만 채워진다. 기준 좌표 자체는 저장하지 않는다 (공통 명세 §5). */
    val distanceKm: Double?,

    val link: String?,
)
