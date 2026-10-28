package com.yong.travel.trip.record.persistence

/** 여행별 기록 수. */
data class TripRecordCount(val tripId: Long, val count: Long)

/** 기록별 태그 이름. */
data class RecordTagName(val recordId: Long, val name: String)
