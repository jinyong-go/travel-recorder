package com.yong.travel.trip.record.service

import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.tag.service.TagService
import com.yong.travel.trip.domain.TripRef
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.record.domain.GeoPoint
import com.yong.travel.trip.record.domain.RecordListQuery
import com.yong.travel.trip.record.domain.RecordSort
import com.yong.travel.trip.record.domain.TripRecord
import com.yong.travel.trip.record.domain.TripRecordCreateCommand
import com.yong.travel.trip.record.domain.TripRecordUpdateCommand
import com.yong.travel.trip.record.domain.haversineKm
import com.yong.travel.trip.record.domain.roundTo2Decimals
import com.yong.travel.trip.record.persistence.TripRecordEntity
import com.yong.travel.trip.record.persistence.TripRecordRepository
import com.yong.travel.trip.record.persistence.TripRecordSpecifications
import com.yong.travel.trip.service.TripService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 기록의 조회·등록·수정·이동·삭제를 맡는다. 권한은 소속 여행이 정한다. */
@Service
@Transactional(readOnly = true)
class TripRecordService(
    private val recordRepository: TripRecordRepository,
    private val tagService: TagService,
    private val photoRepository: PhotoRepository,
    private val photoDatabaseService: PhotoDatabaseService,
    private val tripService: TripService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 볼 수 있는 기록을 조건에 맞춰 페이지로 돌려준다.
     *
     * @param userId 비로그인이면 null
     * @param page 0부터 시작
     * @throws ApiException `TRIP_NOT_FOUND` — `tripId` 의 여행이 없거나 볼 권한이 없을 때
     */
    fun list(
        query: RecordListQuery,
        userId: Long?,
        page: Int,
    ): PageResult<TripRecord> {
        // 볼 수 없는 여행이면 빈 목록이 아니라 404 로 답한다.
        query.tripId?.let { tripService.requireViewable(it, userId) }

        val filtered = TripRecordSpecifications.withScope(query.scope, userId)
            .and(TripRecordSpecifications.withFilters(query.tripId, query.category, query.tag, query.keyword))

        // 거리순은 조회 조건 쪽에서 정렬하므로 페이지 요청의 정렬은 비운다.
        val spec = if (query.sort == RecordSort.DISTANCE) {
            filtered.and(TripRecordSpecifications.orderByDistanceFrom(requireNotNull(query.origin)))
        } else {
            filtered
        }
        val sort = when (query.sort) {
            RecordSort.RECENT -> Sort.by(Sort.Direction.DESC, "createdAt")
            RecordSort.OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt")
            RecordSort.RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
            RecordSort.DISTANCE -> Sort.unsorted()
        }
        val found = recordRepository.findAll(spec, listPageRequest(page, sort))

        val recordIds = found.content.mapNotNull { it.id }
        val photos = photosOf(recordIds)
        val tags = tagsOf(recordIds)
        val records = found.toPageResult().map {
            val recordId = requireNotNull(it.id)
            it.toDomain(photos[recordId].orEmpty(), tags[recordId].orEmpty(), query.origin?.let { origin -> distanceOf(it, origin) })
        }

        // 기준 좌표는 로그에 남기지 않는다.
        log.debug(
            "기록 목록 조회 scope={} tripId={} userId={} count={}",
            query.scope, query.tripId, userId, found.totalElements,
        )
        return records
    }

    /**
     * 기록을 조회한다.
     *
     * @param userId 비로그인이면 null
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     */
    fun get(recordId: Long, userId: Long?): TripRecord = detailOf(findViewableRecord(recordId, userId))

    /**
     * 기록을 등록한다. 요청자가 소유한 여행에만 등록할 수 있다.
     *
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 요청자의 여행이 아닐 때
     */
    @Transactional
    fun create(authorId: Long, command: TripRecordCreateCommand): TripRecord {
        val trip = tripService.findOwnedTrip(command.tripId, authorId)
        val record = TripRecordEntity(
            trip = trip,
            name = command.name,
            category = command.category,
            address = command.address,
            roadAddress = command.roadAddress,
            externalLink = command.externalLink,
            latitude = command.latitude,
            longitude = command.longitude,
            rating = command.rating,
            memo = command.memo,
        )
        record.tags = tagService.findOrCreateAll(command.tags).toMutableSet()
        val saved = recordRepository.save(record)
        log.debug(
            "기록 생성 recordId={} tripId={} authorId={} category={}",
            saved.id, trip.id, authorId, saved.category,
        )
        return detailOf(saved)
    }

    /**
     * 기록 내용을 바꾼다. 여행 이동은 [changeTrip] 으로 한다.
     *
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     */
    @Transactional
    fun update(
        recordId: Long,
        authorId: Long,
        command: TripRecordUpdateCommand,
    ): TripRecord {
        val record = findEditableRecord(recordId, authorId)

        record.name = command.name
        record.category = command.category
        record.address = command.address
        record.roadAddress = command.roadAddress
        record.externalLink = command.externalLink
        record.latitude = command.latitude
        record.longitude = command.longitude
        record.rating = command.rating
        record.memo = command.memo
        record.replaceTags(tagService.findOrCreateAll(command.tags))

        // 응답에 수정 시각이 담기도록 바로 flush 한다.
        val saved = recordRepository.saveAndFlush(record)
        log.debug("기록 수정 recordId={} authorId={}", recordId, authorId)
        return detailOf(saved)
    }

    /**
     * 기록을 다른 여행으로 옮긴다. 공개 범위는 새 여행을 따르고, 이전 여행의 커버였던 사진은 커버를 해제한다.
     *
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     * @throws ApiException `TRIP_NOT_FOUND` — 옮겨 갈 여행이 없거나 요청자의 여행이 아닐 때
     */
    @Transactional
    fun changeTrip(
        recordId: Long,
        authorId: Long,
        tripId: Long,
    ): TripRecord {
        val record = findEditableRecord(recordId, authorId)

        val newTrip = tripService.findOwnedTrip(tripId, authorId)
        val previousTripId = record.trip.id
        if (newTrip.id != previousTripId) {
            record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
            record.trip = newTrip
        }
        val saved = recordRepository.saveAndFlush(record)
        if (newTrip.id != previousTripId) {
            log.debug(
                "기록 여행 이동 recordId={} authorId={} fromTripId={} toTripId={}",
                recordId, authorId, previousTripId, newTrip.id,
            )
        }
        return detailOf(saved)
    }

    /**
     * 기록을 지운다. 사진이 URL 로 계속 받아지지 않도록 바이너리도 지우고, 커버였다면 커버를 해제한다.
     *
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     */
    @Transactional
    fun delete(recordId: Long, authorId: Long) {
        val record = findEditableRecord(recordId, authorId)

        record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
        record.softDelete()
        val purged = photoDatabaseService.deleteByRecord(recordId)
        log.debug("기록 삭제 recordId={} tripId={} authorId={} purgedPhotoCount={}", recordId, record.trip.id, authorId, purged)
    }

    /**
     * 요청자가 편집할 수 있는 기록을 읽는다.
     * 볼 수 없는 기록은 존재를 숨기도록 404, 볼 수 있지만 소유자가 아니면 403 으로 답한다.
     *
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 여행 소유자가 아닐 때
     */
    fun findEditableRecord(recordId: Long, userId: Long): TripRecordEntity {
        val record = findViewableRecord(recordId, userId)
        if (record.trip.owner.id != userId) {
            log.debug("기록 작성자 아님 recordId={} userId={} ownerId={}", recordId, userId, record.trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return record
    }

    /** 요청자가 볼 수 있는 기록을 읽는다. 없는 것과 권한 없는 것을 구분하지 않는다. */
    private fun findViewableRecord(recordId: Long, userId: Long?): TripRecordEntity =
        recordRepository.findOne(
            TripRecordSpecifications.withId(recordId).and(TripRecordSpecifications.visibleTo(userId)),
        ).orElseThrow {
            log.debug("기록 조회 차단 recordId={} userId={}", recordId, userId)
            // 여행을 못 봐서 가려져도 기록이 있다는 사실이 드러나지 않도록 RECORD_NOT_FOUND 로 답한다.
            ApiException(ErrorCode.RECORD_NOT_FOUND)
        }

    /** 여러 기록의 사진을 등록순으로 읽어 기록 id 별로 묶는다. */
    private fun photosOf(recordIds: List<Long>): Map<Long, List<PhotoEntity>> {
        if (recordIds.isEmpty()) return emptyMap()
        return photoRepository.findByRecordIdInOrderByCreatedAtAsc(recordIds)
            .groupBy { requireNotNull(it.record.id) }
    }

    /** 여러 기록의 태그 이름을 읽어 기록 id 별로 묶는다. */
    private fun tagsOf(recordIds: List<Long>): Map<Long, List<String>> {
        if (recordIds.isEmpty()) return emptyMap()
        return recordRepository.findTagNamesByRecordIds(recordIds)
            .groupBy({ it.recordId }) { it.name }
    }

    private fun PhotoEntity.toDomain(): Photo {
        val id = requireNotNull(id)
        return Photo(id, photoDatabaseService.urlOf(id))
    }

    private fun TripEntity.toDomain() = TripRef(id = requireNotNull(id), name = name)

    /** 기록에 사진·태그·거리를 붙여 도메인 객체로 만든다. */
    private fun TripRecordEntity.toDomain(
        photos: List<PhotoEntity>,
        tagNames: List<String>,
        distanceKm: Double?,
    ): TripRecord =
        TripRecord(
            id = requireNotNull(id),
            trip = trip.toDomain(),
            name = name,
            category = category,
            tags = tagNames.sorted(),
            address = address,
            roadAddress = roadAddress,
            externalLink = externalLink,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            photos = photos.map { it.toDomain() },
            author = trip.owner.toUser(),
            distanceKm = distanceKm,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /** 기록 하나를 사진·태그와 함께 도메인 객체로 만든다. 거리는 목록에서만 계산한다. */
    private fun detailOf(record: TripRecordEntity): TripRecord {
        val photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(requireNotNull(record.id))
        return record.toDomain(photos, record.tags.map { it.name }, distanceKm = null)
    }

    /** 기준 위치에서 기록까지의 거리(km). */
    private fun distanceOf(record: TripRecordEntity, origin: GeoPoint): Double =
        haversineKm(origin.latitude, origin.longitude, record.latitude, record.longitude).roundTo2Decimals()
}
