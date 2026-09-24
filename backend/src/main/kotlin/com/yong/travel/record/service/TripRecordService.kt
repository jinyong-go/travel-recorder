package com.yong.travel.record.service

import com.yong.travel.auth.domain.User
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.domain.Photo
import com.yong.travel.photo.persistence.PhotoEntity
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.domain.RecordDetail
import com.yong.travel.record.domain.RecordSummary
import com.yong.travel.record.domain.RecordListQuery
import com.yong.travel.record.domain.RecordSort
import com.yong.travel.record.domain.TripRecordCreateCommand
import com.yong.travel.record.domain.TripRecordUpdateCommand
import com.yong.travel.record.persistence.TripRecordEntity
import com.yong.travel.record.persistence.TripRecordRepository
import com.yong.travel.record.persistence.TripRecordSpecifications
import com.yong.travel.tag.service.TagService
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripRepository
import com.yong.travel.trip.service.TripService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripRecordService(
    private val recordRepository: TripRecordRepository,
    private val tripRepository: TripRepository,
    private val tagService: TagService,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val groupService: GroupService,
    private val tripService: TripService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(
        query: RecordListQuery,
        userId: Long?,
        pageable: Pageable,
    ): Page<RecordSummary> {
        // tripId 로 좁히면 그 여행을 볼 수 있는지 먼저 판정한다. scope 유무와 무관하게 못 보면
        // 없는 여행과 같은 404 다 (명세 §4.4.1).
        query.tripId?.let { tripService.requireViewable(it, userId) }

        // scope 가 없으면 tripId 가 있다는 뜻이고(컨트롤러가 보장), 그 여행을 볼 수 있음은 위에서
        // 확인했다. 볼 수 있는 여행의 하위 기록은 전부 보이므로 범위 조건을 더하지 않는다 (공통 명세 §3.5).
        val scopeSpec = query.scope
            ?.let { TripRecordSpecifications.withScope(it, userId, groupService.groupIdsOf(userId)) }
            ?: Specification.unrestricted()
        val spec = scopeSpec
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
            // 여행 상세는 여행을 따라가며 읽으므로 등록순이다 (명세 §4.4.1).
            RecordSort.OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt")
            RecordSort.RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val page = recordRepository.findAll(spec, PageRequest.of(pageable.pageNumber, pageable.pageSize, sort))

        // 사진은 항목마다 묻지 않고 페이지 전체를 한 번에 읽는다.
        val photos = photosOf(page.content.mapNotNull { it.id })
        val summaries = page.content.map { it.toSummary(photos, query.lat, query.lng) }
        val sorted = if (query.sort == RecordSort.DISTANCE) {
            summaries.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            summaries
        }

        // 범위 판정이 쿼리 단계에 있어(명세 §7) 건수가 어긋나면 곧 유출이다. 기준 좌표는 남기지 않는다.
        log.debug(
            "기록 목록 scope={} tripId={} userId={} 건수={}",
            query.scope, query.tripId, userId, page.totalElements,
        )
        return PageImpl(sorted, page.pageable, page.totalElements)
    }

    /** 볼 권한이 없으면 없는 기록과 똑같이 RECORD_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(recordId: Long, userId: Long?): RecordDetail {
        val record = findRecord(recordId)
        requireViewable(record, userId)
        return record.toDetail()
    }

    /** 소속 여행은 요청자가 소유한 것이어야 한다. 아니면 TRIP_NOT_FOUND 다. */
    @Transactional
    fun create(authorId: Long, command: TripRecordCreateCommand): RecordDetail {
        val trip = requireOwnedTrip(command.tripId, authorId)
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
        return saved.toDetail()
    }

    @Transactional
    fun update(
        recordId: Long,
        authorId: Long,
        command: TripRecordUpdateCommand,
    ): RecordDetail {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.name = command.name
        record.category = command.category
        record.address = command.address
        record.roadAddress = command.roadAddress
        record.externalLink = command.externalLink
        record.latitude = command.latitude
        record.longitude = command.longitude
        record.rating = command.rating
        record.memo = command.memo
        record.tags = tagService.findOrCreateAll(command.tags).toMutableSet()

        log.debug("기록 수정 recordId={} authorId={}", recordId, authorId)
        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        return recordRepository.saveAndFlush(record).toDetail()
    }

    /**
     * 소속 여행 변경. 옮기는 순간 이 기록의 공개 범위는 새 여행의 것이 된다 (명세 §4.4).
     *
     * 이 기록의 사진이 이전 여행의 커버였다면 그 지정을 푼다 — 커버는 자기 여행의 사진만
     * 가리킬 수 있기 때문이다 (명세 §4.4, §3.1).
     */
    @Transactional
    fun changeTrip(
        recordId: Long,
        authorId: Long,
        tripId: Long,
    ): RecordDetail {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        val newTrip = requireOwnedTrip(tripId, authorId)
        if (newTrip.id != record.trip.id) {
            log.debug(
                "기록 여행 이동 recordId={} authorId={} {} -> {} (공개 범위가 새 여행의 것이 된다)",
                recordId, authorId, record.trip.id, newTrip.id,
            )
            record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
            record.trip = newTrip
        }
        return recordRepository.saveAndFlush(record).toDetail()
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
    fun delete(recordId: Long, authorId: Long) {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
        record.softDelete()
        log.debug("기록 삭제 recordId={} tripId={} authorId={}", recordId, record.trip.id, authorId)
    }

    /**
     * 기록을 담거나 옮길 여행을 찾는다. **소유한 여행이 아니면 404** 다 —
     * 남의 여행에 기록을 넣을 수 없고, 403 으로 답하면 그 여행의 존재가 드러난다 (명세 §2.2).
     */
    private fun requireOwnedTrip(tripId: Long, userId: Long): TripEntity {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { ApiException(ErrorCode.TRIP_NOT_FOUND) }
        if (trip.owner.id != userId) {
            // 응답이 "없는 여행"과 같으므로 남의 여행이라 막혔다는 사실은 이 로그에만 남는다.
            log.debug("여행 소유자 아님 tripId={} userId={} ownerId={}", tripId, userId, trip.owner.id)
            throw ApiException(ErrorCode.TRIP_NOT_FOUND)
        }
        return trip
    }

    private fun findRecord(recordId: Long): TripRecordEntity =
        recordRepository.findById(recordId)
            .orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }

    private fun requireViewable(record: TripRecordEntity, userId: Long?) {
        if (canView(record, userId)) return
        // 존재 은닉 때문에 응답이 "없음"과 같다. 왜 가려졌는지는 이 로그에만 드러난다 (명세 §2.2).
        log.debug(
            "기록 열람 차단 recordId={} tripId={} userId={} visibility={}",
            record.id, record.trip.id, userId, record.trip.visibility,
        )
        // 권한 없음(403)이 아니라 없는 기록(404)으로 답한다. 403 은 "그 기록이 있다"는 뜻이 되기 때문이다.
        // 소속 여행을 못 봐서 가려지는 경우도 RECORD_NOT_FOUND 다. TRIP_NOT_FOUND 를 내려주면
        // "기록은 있는데 여행을 못 본다" 는 사실이 새어 나간다 (명세 §2.2).
        throw ApiException(ErrorCode.RECORD_NOT_FOUND)
    }

    /** 기록을 볼 수 있는지는 **전적으로 소속 여행이 정한다** (명세 §3.5). 판정식은 여행 서비스에 하나만 둔다. */
    private fun canView(record: TripRecordEntity, userId: Long?): Boolean = tripService.canView(record.trip, userId)

    /** 고칠 수 있는 사람은 여행 소유자뿐이다. 기록에는 작성자 컬럼이 없다 (명세 §3.1). */
    private fun requireAuthor(record: TripRecordEntity, userId: Long) {
        // 볼 수도 없는 기록이면 존재부터 숨긴다. 볼 수 있는데 소유자가 아닌 경우에만 403 이다.
        requireViewable(record, userId)
        if (record.trip.owner.id != userId) {
            log.debug("기록 작성자 아님 recordId={} userId={} ownerId={}", record.id, userId, record.trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 여러 기록의 사진을 한 번에 읽어 기록 id 로 묶는다. 가입 순서(createdAt)는 그대로 지켜진다. */
    private fun photosOf(recordIds: List<Long>): Map<Long, List<PhotoEntity>> {
        if (recordIds.isEmpty()) return emptyMap()
        return photoRepository.findByRecordIdInOrderByCreatedAtAsc(recordIds)
            .groupBy { requireNotNull(it.record.id) }
    }

    private fun PhotoEntity.toDomain() = Photo(requireNotNull(id), photoStorageService.resolveUrl(storageKey))

    private fun TripEntity.toDomain() = Trip(id = requireNotNull(id), name = name)

    private fun TripEntity.toOwner() = User(requireNotNull(owner.id), owner.name, owner.profileImageUrl)

    /**
     * 기록 엔티티에 사진을 붙여 [RecordDetail] 로 만든다.
     *
     * 조회가 여기서 끝난다 — 이 함수를 지나면 응답을 만들며 리포지토리를 다시 부를 일이 없어야 한다.
     */
    private fun TripRecordEntity.toDetail(): RecordDetail {
        val recordId = requireNotNull(id)
        return RecordDetail(
            id = recordId,
            trip = trip.toDomain(),
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
            photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId).map { it.toDomain() },
            author = trip.toOwner(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /** 목록 한 줄. 사진은 호출부가 한 번에 모아 온 것을 받는다. */
    private fun TripRecordEntity.toSummary(
        photos: Map<Long, List<PhotoEntity>>,
        lat: Double?,
        lng: Double?,
    ): RecordSummary {
        val recordId = requireNotNull(id)
        val mine = photos[recordId].orEmpty()
        return RecordSummary(
            id = recordId,
            trip = trip.toDomain(),
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            thumbnailUrl = mine.firstOrNull()?.let { photoStorageService.resolveUrl(it.storageKey) },
            photoCount = mine.size.toLong(),
            author = trip.toOwner(),
            distanceKm = if (lat != null && lng != null) {
                haversineKm(lat, lng, latitude, longitude).roundTo2Decimals()
            } else {
                null
            },
            createdAt = createdAt,
        )
    }
}
