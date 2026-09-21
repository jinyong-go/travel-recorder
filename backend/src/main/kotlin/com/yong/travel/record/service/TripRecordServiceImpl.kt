package com.yong.travel.record.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.domain.TripRecord
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.RecordSort
import com.yong.travel.record.dto.TripChangeRequest
import com.yong.travel.record.dto.TripRecordCreateRequest
import com.yong.travel.record.dto.TripRecordResponse
import com.yong.travel.record.dto.TripRecordSummaryResponse
import com.yong.travel.record.dto.TripRecordUpdateRequest
import com.yong.travel.record.repository.TripRecordRepository
import com.yong.travel.record.repository.TripRecordSpecifications
import com.yong.travel.tag.service.TagService
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.dto.TripRefResponse
import com.yong.travel.trip.repository.TripRepository
import com.yong.travel.trip.repository.TripShareRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripRecordServiceImpl(
    private val recordRepository: TripRecordRepository,
    private val tripRepository: TripRepository,
    private val tagService: TagService,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val groupService: GroupService,
    private val tripShareRepository: TripShareRepository,
) : TripRecordService {

    override fun list(
        query: RecordListQuery,
        userId: Long?,
        pageable: Pageable,
    ): PageResponse<TripRecordSummaryResponse> {
        val groupIds = groupService.groupIdsOf(userId)
        val spec = TripRecordSpecifications.withScope(query.scope, userId, groupIds)
            .and(
                TripRecordSpecifications.withFilters(
                    query.tripId,
                    query.category,
                    query.tag,
                    query.keyword,
                ),
            )

        // 평점은 기록의 컬럼이라 DB 에서 그대로 정렬된다.
        // 거리만 저장된 값이 아니라 요청 좌표에 따라 매번 달라져 페이지 안에서 재정렬한다.
        val sort = when (query.sort) {
            RecordSort.RECENT, RecordSort.DISTANCE -> Sort.by(Sort.Direction.DESC, "createdAt")
            RecordSort.RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val page = recordRepository.findAll(spec, PageRequest.of(pageable.pageNumber, pageable.pageSize, sort))

        val summaries = page.content.map { it.toSummaryResponse(userId, query.lat, query.lng) }
        val sorted = if (query.sort == RecordSort.DISTANCE) {
            summaries.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            summaries
        }

        return PageResponse(sorted, page.number, page.size, page.totalElements, page.totalPages)
    }

    override fun get(recordId: Long, userId: Long?): TripRecordResponse {
        val record = findRecord(recordId)
        requireViewable(record, userId)
        return record.toResponse(userId)
    }

    @Transactional
    override fun create(authorId: Long, request: TripRecordCreateRequest): TripRecordResponse {
        val trip = requireOwnedTrip(request.tripId, authorId)
        val record = TripRecord(
            trip = trip,
            name = request.name,
            category = request.category,
            address = request.address,
            roadAddress = request.roadAddress,
            externalLink = request.externalLink,
            latitude = request.latitude,
            longitude = request.longitude,
            rating = request.rating,
            memo = request.memo,
        )
        record.tags = tagService.findOrCreateAll(request.tags).toMutableSet()
        return recordRepository.save(record).toResponse(authorId)
    }

    @Transactional
    override fun update(
        recordId: Long,
        authorId: Long,
        request: TripRecordUpdateRequest,
    ): TripRecordResponse {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.name = request.name
        record.category = request.category
        record.address = request.address
        record.roadAddress = request.roadAddress
        record.externalLink = request.externalLink
        record.latitude = request.latitude
        record.longitude = request.longitude
        record.rating = request.rating
        record.memo = request.memo
        record.tags = tagService.findOrCreateAll(request.tags).toMutableSet()

        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        return recordRepository.saveAndFlush(record).toResponse(authorId)
    }

    /**
     * 소속 여행 변경. 옮기는 순간 이 기록의 공개 범위는 새 여행의 것이 된다 (명세 §4.4).
     *
     * 이 기록의 사진이 이전 여행의 커버였다면 그 지정을 푼다 — 커버는 자기 여행의 사진만
     * 가리킬 수 있기 때문이다 (명세 §4.4, §3.1).
     */
    @Transactional
    override fun changeTrip(
        recordId: Long,
        authorId: Long,
        request: TripChangeRequest,
    ): TripRecordResponse {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        val newTrip = requireOwnedTrip(request.tripId, authorId)
        if (newTrip.id != record.trip.id) {
            record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
            record.trip = newTrip
        }
        return recordRepository.saveAndFlush(record).toResponse(authorId)
    }

    /**
     * soft delete. 사진은 soft delete 대상이 아니라 파일과 메타데이터를 그대로 두는데,
     * 기록이 보이지 않는 동안에는 어차피 조회 경로가 없다.
     *
     * 다만 이 기록의 사진이 여행 커버였다면 지정을 푼다. 지워진 기록의 사진이 여행을 계속
     * 대표하게 두면 "커버는 그 여행의 하위 기록에 속한 사진" 이라는 불변식이 깨진다 (명세 §3.2).
     *
     * 공유 관계는 손대지 않는다 — 공유는 여행에 걸려 있고 기록은 갖고 있지 않다.
     */
    @Transactional
    override fun delete(recordId: Long, authorId: Long) {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
        record.softDelete()
    }

    /**
     * 기록을 담거나 옮길 여행을 찾는다. **소유한 여행이 아니면 404** 다 —
     * 남의 여행에 기록을 넣을 수 없고, 403 으로 답하면 그 여행의 존재가 드러난다 (명세 §2.2).
     */
    private fun requireOwnedTrip(tripId: Long, userId: Long): Trip {
        val trip = tripRepository.findById(tripId).orElseThrow { ApiException(ErrorCode.TRIP_NOT_FOUND) }
        if (trip.owner.id != userId) throw ApiException(ErrorCode.TRIP_NOT_FOUND)
        return trip
    }

    private fun findRecord(recordId: Long): TripRecord =
        recordRepository.findById(recordId).orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }

    private fun requireViewable(record: TripRecord, userId: Long?) {
        if (canView(record, userId)) return
        // 권한 없음(403)이 아니라 없는 기록(404)으로 답한다. 403 은 "그 기록이 있다"는 뜻이 되기 때문이다.
        // 소속 여행을 못 봐서 가려지는 경우도 RECORD_NOT_FOUND 다. TRIP_NOT_FOUND 를 내려주면
        // "기록은 있는데 여행을 못 본다" 는 사실이 새어 나간다 (명세 §2.2).
        throw ApiException(ErrorCode.RECORD_NOT_FOUND)
    }

    /**
     * 기록을 볼 수 있는지는 **전적으로 소속 여행이 정한다** (명세 §3.5).
     *
     * 내 여행이거나, 전체 공개 여행이거나, 내가 속한 그룹으로 공유된 여행이거나 셋 중 하나다.
     */
    private fun canView(record: TripRecord, userId: Long?): Boolean {
        val trip = record.trip
        if (userId != null && trip.owner.id == userId) return true
        if (trip.visibility == Visibility.PUBLIC) return true
        if (trip.visibility != Visibility.GROUP || userId == null) return false

        val groupIds = groupService.groupIdsOf(userId)
        return groupIds.isNotEmpty() &&
            tripShareRepository.existsByTripIdAndGroupIdIn(requireNotNull(trip.id), groupIds)
    }

    /** 고칠 수 있는 사람은 여행 소유자뿐이다. 기록에는 작성자 컬럼이 없다 (명세 §3.1). */
    private fun requireAuthor(record: TripRecord, userId: Long) {
        // 볼 수도 없는 기록이면 존재부터 숨긴다. 볼 수 있는데 소유자가 아닌 경우에만 403 이다.
        requireViewable(record, userId)
        if (record.trip.owner.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun Trip.toRef(): TripRefResponse =
        TripRefResponse(id = requireNotNull(id), name = name)

    private fun TripRecord.toResponse(requesterId: Long?): TripRecordResponse {
        val recordId = requireNotNull(id)
        val owner = trip.owner
        return TripRecordResponse(
            id = recordId,
            trip = trip.toRef(),
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            roadAddress = roadAddress,
            externalLink = externalLink,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId)
                .map { PhotoResponse(requireNotNull(it.id), photoStorageService.resolveUrl(it.storageKey)) },
            author = owner.toResponse(),
            isAuthor = requesterId != null && owner.id == requesterId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun TripRecord.toSummaryResponse(
        requesterId: Long?,
        lat: Double?,
        lng: Double?,
    ): TripRecordSummaryResponse {
        val recordId = requireNotNull(id)
        val owner = trip.owner
        val photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId)
        return TripRecordSummaryResponse(
            id = recordId,
            trip = trip.toRef(),
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            thumbnailUrl = photos.firstOrNull()?.let { photoStorageService.resolveUrl(it.storageKey) },
            photoCount = photos.size.toLong(),
            author = owner.toResponse(),
            isAuthor = requesterId != null && owner.id == requesterId,
            distanceKm = if (lat != null && lng != null) {
                haversineKm(lat, lng, latitude, longitude).roundTo2Decimals()
            } else {
                null
            },
            createdAt = createdAt,
        )
    }
}
