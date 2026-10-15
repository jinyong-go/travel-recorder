package com.yong.travel.trip.record.persistence

/** 여행 하나의 살아 있는 기록 수. 여행 목록의 기록 수 일괄 집계 결과 한 행이다. */
data class TripRecordCount(val tripId: Long, val count: Long)

/** 기록 하나에 달린 태그 이름. 기록 목록의 태그 일괄 조회 결과 한 행이다. */
data class RecordTagName(val recordId: Long, val name: String)
