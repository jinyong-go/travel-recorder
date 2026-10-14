package com.yong.travel.trip.record.service

import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
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
import com.yong.travel.trip.record.persistence.TripRecordEntity
import com.yong.travel.trip.record.persistence.TripRecordRepository
import com.yong.travel.trip.record.persistence.TripRecordSpecifications
import com.yong.travel.trip.service.TripService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 여행 기록의 조회·등록·수정·이동·삭제.
 *
 * 기록의 열람과 편집 권한은 전적으로 소속 여행이 정한다. 열람 판정은 소속 여행을 조인한 조회 조건으로 한다.
 */
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
     * 범위 판정과 정렬은 모두 조회 쿼리 단계에서 한다. 거리순도 DB 가 기준 위치로 정렬하므로 페이지를
     * 넘어 이어진다.
     *
     * @param query 범위·여행·카테고리·태그·검색어·정렬·기준 좌표
     * @param userId 요청자의 사용자 id. 비로그인이면 null
     * @param page 0부터 시작하는 페이지 번호
     * @return 기록 한 페이지
     * @throws ApiException `TRIP_NOT_FOUND` — `tripId` 로 좁혔는데 그 여행이 없거나 볼 권한이 없을 때
     */
    fun list(
        query: RecordListQuery,
        userId: Long?,
        page: Int,
    ): PageResult<TripRecord> {
        // tripId 로 좁히면 그 여행을 볼 수 있는지 먼저 판정한다. scope 유무와 무관하게 못 보면
        // 빈 목록이 아니라 없는 여행과 같은 404 다. 열람 판정 자체는 아래 쿼리에도 실려 있다.
        query.tripId?.let { tripService.requireViewable(it, userId) }

        val filtered = TripRecordSpecifications.withScope(query.scope, userId)
            .and(TripRecordSpecifications.withFilters(query.tripId, query.category, query.tag, query.keyword))

        // 거리는 저장된 값이 아니라 요청 좌표에 따라 달라져 Sort 로 표현할 수 없다. DB 가 위치 인덱스로
        // 정렬하도록 조건 쪽에 정렬을 싣고, 페이지 요청의 정렬은 비운다. 좌표는 컨트롤러가 보장한다.
        val spec = if (query.sort == RecordSort.DISTANCE) {
            filtered.and(TripRecordSpecifications.orderByDistanceFrom(requireNotNull(query.origin)))
        } else {
            filtered
        }
        val sort = when (query.sort) {
            RecordSort.RECENT -> Sort.by(Sort.Direction.DESC, "createdAt")
            // 여행 상세는 여행을 따라가며 읽으므로 등록순이다.
            RecordSort.OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt")
            RecordSort.RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
            RecordSort.DISTANCE -> Sort.unsorted()
        }
        val found = recordRepository.findAll(spec, listPageRequest(page, sort))

        // 사진은 항목마다 묻지 않고 페이지 전체를 한 번에 읽는다.
        val recordIds = found.content.mapNotNull { it.id }
        val photos = photosOf(recordIds)
        val tags = tagsOf(recordIds)
        val records = found.toPageResult().map {
            val recordId = requireNotNull(it.id)
            it.toDomain(photos[recordId].orEmpty(), tags[recordId].orEmpty(), query.origin?.let { origin -> distanceOf(it, origin) })
        }

        // 범위 판정이 쿼리 단계에 있어 건수가 어긋나면 곧 유출이다. 기준 좌표는 남기지 않는다.
        log.debug(
            "기록 목록 scope={} tripId={} userId={} count={}",
            query.scope, query.tripId, userId, found.totalElements,
        )
        return records
    }

    /**
     * 기록 상세를 조회한다.
     *
     * @param recordId 조회할 기록 id
     * @param userId 요청자의 사용자 id. 비로그인이면 null
     * @return 사진·태그까지 담긴 기록 상세
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때 (존재 은닉)
     */
    fun get(recordId: Long, userId: Long?): TripRecord = detailOf(findViewableRecord(recordId, userId))

    /**
     * 기록을 등록한다. 소속 여행은 요청자가 소유한 것이어야 한다.
     *
     * @param authorId 요청자의 사용자 id
     * @param command 기록 입력값
     * @return 등록된 기록 상세
     * @throws ApiException `TRIP_NOT_FOUND` — 소속 여행이 없거나 요청자의 여행이 아닐 때
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
     * 기록 내용을 덮어쓴다. 소속 여행은 바꾸지 않으며, 그것은 [changeTrip] 의 몫이다.
     *
     * @param recordId 수정할 기록 id
     * @param authorId 요청자의 사용자 id. 소속 여행의 소유자여야 한다
     * @param command 새 기록 내용
     * @return 수정된 기록 상세
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
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

        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        val saved = recordRepository.saveAndFlush(record)
        log.debug("기록 수정 recordId={} authorId={}", recordId, authorId)
        return detailOf(saved)
    }

    /**
     * 소속 여행 변경. 옮기는 순간 이 기록의 공개 범위는 새 여행의 것이 된다.
     *
     * 이 기록의 사진이 이전 여행의 커버였다면 그 지정을 푼다 — 커버는 자기 여행의 사진만
     * 가리킬 수 있기 때문이다.
     *
     * @param recordId 옮길 기록 id
     * @param authorId 요청자의 사용자 id. 기록의 현재 여행과 새 여행 모두의 소유자여야 한다
     * @param tripId 옮겨 갈 여행 id
     * @return 옮겨진 기록 상세
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
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
     * soft delete. 사진 메타데이터는 남기고 바이너리는 지운다. 남겨 두면 지운 기록의 사진을 URL 로
     * 계속 받아 갈 수 있고 저장 공간도 돌아오지 않는다. 그래서 기록을 되살려도 사진은 돌아오지 않는다.
     *
     * 다만 이 기록의 사진이 여행 커버였다면 지정을 푼다. 지워진 기록의 사진이 여행을 계속
     * 대표하게 두면 "커버는 그 여행의 하위 기록에 속한 사진" 이라는 불변식이 깨진다.
     *
     * 공유 관계는 손대지 않는다 — 공유는 여행에 걸려 있고 기록은 갖고 있지 않다.
     *
     * @param recordId 삭제할 기록 id
     * @param authorId 요청자의 사용자 id. 소속 여행의 소유자여야 한다
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     */
    @Transactional
    fun delete(recordId: Long, authorId: Long) {
        val record = findEditableRecord(recordId, authorId)

        record.trip.clearCoverIfAmong(photoRepository.findIdsByRecordId(recordId))
        record.softDelete()
        val purged = photoDatabaseService.deleteByRecord(recordId)
        log.debug("기록 삭제 recordId={} tripId={} authorId={} photos={}", recordId, record.trip.id, authorId, purged)
    }

    /**
     * 편집할 기록을 읽는다. 기록 수정·이동·삭제와 사진 업로드·삭제가 함께 쓰는 관문이다.
     *
     * 고칠 수 있는 사람은 소속 여행의 소유자뿐이다 — 기록에는 작성자 컬럼이 없다.
     *
     * 엔티티를 반환한다. 컨트롤러로 나가는 경계가 아니라 PhotoService 가 사진을 기록에 걸 때 쓰는
     * 서비스 간 호출이기 때문이다. 판정을 여기 하나로 모아 사진과 기록의 실패 응답이 갈라지지 않게 한다.
     *
     * @param recordId 기록 id
     * @param userId 요청자의 사용자 id. 소속 여행의 소유자여야 한다
     * @return 편집할 기록
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소속 여행의 소유자가 아닐 때
     */
    fun findEditableRecord(recordId: Long, userId: Long): TripRecordEntity {
        // 볼 수도 없는 기록이면 존재부터 숨긴다. 존재를 이미 아는 사람에게만 403 으로 답한다.
        val record = findViewableRecord(recordId, userId)
        if (record.trip.owner.id != userId) {
            log.debug("기록 작성자 아님 recordId={} userId={} ownerId={}", recordId, userId, record.trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return record
    }

    /**
     * 요청자가 볼 수 있는 기록을 읽는다. 기록을 볼 수 있는지는 전적으로 소속 여행이 정한다.
     *
     * 판정이 조회 조건에 실려 있어 "없음"과 "권한 없음"이 같은 빈 결과로 나온다.
     */
    private fun findViewableRecord(recordId: Long, userId: Long?): TripRecordEntity =
        recordRepository.findOne(
            TripRecordSpecifications.withId(recordId).and(TripRecordSpecifications.visibleTo(userId)),
        ).orElseThrow {
            // 응답이 "없음"과 같으므로 막혔을 수 있다는 사실은 이 로그에만 남는다.
            log.debug("기록 조회 차단 recordId={} userId={}", recordId, userId)
            // 소속 여행을 못 봐서 가려지는 경우도 RECORD_NOT_FOUND 다. TRIP_NOT_FOUND 를 내려주면
            // "기록은 있는데 여행을 못 본다" 는 사실이 새어 나간다.
            ApiException(ErrorCode.RECORD_NOT_FOUND)
        }

    /** 여러 기록의 사진을 한 번에 읽어 기록 id 로 묶는다. 등록 순서(createdAt)는 그대로 지켜진다. */
    private fun photosOf(recordIds: List<Long>): Map<Long, List<PhotoEntity>> {
        if (recordIds.isEmpty()) return emptyMap()
        return photoRepository.findByRecordIdInOrderByCreatedAtAsc(recordIds)
            .groupBy { requireNotNull(it.record.id) }
    }

    /** 여러 기록의 태그 이름을 한 번에 읽어 기록 id 로 묶는다. 태그가 없는 기록은 빠진다. */
    private fun tagsOf(recordIds: List<Long>): Map<Long, List<String>> {
        if (recordIds.isEmpty()) return emptyMap()
        return recordRepository.findTagNamesByRecordIds(recordIds)
            .groupBy({ (it[0] as Number).toLong() }) { it[1] as String }
    }

    /** 사진 엔티티 → 접근 URL 을 붙인 도메인 값. */
    private fun PhotoEntity.toDomain(): Photo {
        val id = requireNotNull(id)
        return Photo(id, photoDatabaseService.urlOf(id))
    }

    /** 여행 엔티티 → 기록에 실리는 소속 여행 참조. */
    private fun TripEntity.toDomain() = TripRef(id = requireNotNull(id), name = name)

    /**
     * 기록 엔티티에 사진·거리를 붙여 [TripRecord] 로 만든다. 목록과 상세가 함께 쓴다.
     *
     * 사진·태그는 호출부가 미리 읽어 온다 — 목록에서 항목마다 조회하지 않기 위해서다.
     * 조회가 여기서 끝난다. 이 함수를 지나면 응답을 만들며 리포지토리를 다시 부를 일이 없어야 한다.
     */
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

    /** 단건 응답. 이 기록의 사진·태그만 읽고, 거리는 목록에서만 계산하므로 비운다. */
    private fun detailOf(record: TripRecordEntity): TripRecord {
        val photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(requireNotNull(record.id))
        return record.toDomain(photos, record.tags.map { it.name }, distanceKm = null)
    }

    /** 기준 위치에서 기록까지의 거리(km). 기준 위치는 계산에만 쓰고 남기지 않는다. */
    private fun distanceOf(record: TripRecordEntity, origin: GeoPoint): Double =
        haversineKm(origin.latitude, origin.longitude, record.latitude, record.longitude).roundTo2Decimals()
}
