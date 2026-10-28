package com.yong.travel.trip.service

import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripCreateCommand
import com.yong.travel.trip.domain.TripListQuery
import com.yong.travel.trip.domain.TripSort
import com.yong.travel.trip.domain.TripUpdateCommand
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripRepository
import com.yong.travel.trip.persistence.TripShareEntity
import com.yong.travel.trip.persistence.TripShareRepository
import com.yong.travel.trip.persistence.TripSpecifications
import com.yong.travel.trip.record.persistence.TripRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** 여행의 조회·생성·수정·삭제와 공개 범위·커버 지정을 맡는다. */
@Service
@Transactional(readOnly = true)
class TripService(
    private val tripRepository: TripRepository,
    private val tripShareRepository: TripShareRepository,
    private val recordRepository: TripRecordRepository,
    private val userRepository: UserRepository,
    private val groupService: GroupService,
    private val photoRepository: PhotoRepository,
    private val photoDatabaseService: PhotoDatabaseService,
    private val visibilityHistoryService: TripVisibilityHistoryService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 볼 수 있는 여행 중 `scope` 에 맞는 것을 페이지로 돌려준다.
     *
     * @param userId 비로그인이면 null 이며 PUBLIC 만 조회된다
     * @param page 0부터 시작
     */
    fun list(
        query: TripListQuery,
        userId: Long?,
        page: Int,
    ): PageResult<Trip> {
        val spec = TripSpecifications.withScope(query.scope, userId)
            .and(TripSpecifications.withKeyword(query.keyword))

        val sort = when (query.sort) {
            TripSort.RECENT -> Sort.by(Sort.Direction.DESC, "createdAt")
            // 시작일이 같으면 생성 시각으로 순서를 고정한다.
            TripSort.START_DATE ->
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val trips = tripRepository.findAll(spec, listPageRequest(page, sort))

        val counts = recordCounts(trips.content.mapNotNull { it.id })
        // 공유 그룹은 소유자에게만 보여 준다.
        val shares = sharesOf(trips.content.filter { userId != null && it.owner.id == userId })
        log.debug("여행 목록 조회 scope={} userId={} count={}", query.scope, userId, trips.totalElements)
        return trips.toPageResult().map { it.toDomain(userId, counts[requireNotNull(it.id)] ?: 0L, shares) }
    }

    /**
     * 여행을 조회한다. 공개 범위와 공유 그룹은 소유자에게만 채운다.
     *
     * @param userId 비로그인이면 null
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     */
    fun get(tripId: Long, userId: Long?): Trip = detailOf(findViewableTrip(tripId, userId), userId)

    /**
     * 요청자를 소유자로 여행을 만든다. GROUP 이면 공유 그룹도 함께 지정한다.
     *
     * @throws ApiException `UNAUTHENTICATED` — 세션의 사용자가 없을 때
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 있을 때
     */
    @Transactional
    fun create(ownerId: Long, command: TripCreateCommand): Trip {
        val owner = userRepository.findById(ownerId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val trip = tripRepository.save(
            TripEntity(
                owner = owner,
                name = command.name.trim(),
                startDate = command.startDate,
                endDate = command.endDate,
                headcount = command.headcount,
                budget = command.budget,
                memo = command.memo,
                visibility = command.visibility,
            ),
        )
        applyShares(trip, command.visibility, command.groupIds, ownerId)
        log.debug("여행 생성 tripId={} ownerId={} visibility={}", trip.id, ownerId, trip.visibility)
        return detailOf(trip, ownerId)
    }

    /**
     * 여행 기본 정보를 바꾼다. 공개 범위는 [changeVisibility] 로 바꾼다.
     *
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     */
    @Transactional
    fun update(tripId: Long, ownerId: Long, command: TripUpdateCommand): Trip {
        val trip = findEditableTrip(tripId, ownerId)

        trip.name = command.name.trim()
        trip.startDate = command.startDate
        trip.endDate = command.endDate
        trip.headcount = command.headcount
        trip.budget = command.budget
        trip.memo = command.memo

        // 응답에 수정 시각이 담기도록 바로 flush 한다.
        val saved = tripRepository.saveAndFlush(trip)
        log.debug("여행 수정 tripId={} ownerId={}", tripId, ownerId)
        return detailOf(saved, ownerId)
    }

    /**
     * 공개 범위를 바꾼다.
     *
     * @param groupIds GROUP 일 때만 쓰며 기존 공유 그룹을 통째로 바꾼다
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 있을 때
     */
    @Transactional
    fun changeVisibility(
        tripId: Long,
        ownerId: Long,
        visibility: TripVisibility,
        groupIds: List<Long>,
    ): Trip {
        val trip = findEditableTrip(tripId, ownerId)

        val previous = trip.visibility
        trip.visibility = visibility
        applyShares(trip, visibility, groupIds, ownerId)
        val saved = tripRepository.saveAndFlush(trip)
        log.debug("여행 공개 범위 변경 tripId={} ownerId={} from={} to={}", tripId, ownerId, previous, visibility)
        return detailOf(saved, ownerId)
    }

    /**
     * 커버 사진을 지정하거나 해제한다. 이 여행의 기록에 속한 사진만 지정할 수 있다.
     *
     * @param photoId null 이면 해제한다
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 이 여행의 사진이 아닐 때
     */
    @Transactional
    fun changeCover(
        tripId: Long,
        ownerId: Long,
        photoId: UUID?,
    ): Trip {
        val trip = findEditableTrip(tripId, ownerId)

        trip.coverPhoto = photoId?.let {
            // 다른 여행의 사진인지 없는 사진인지 드러나지 않게 같은 오류로 답한다.
            photoRepository.findByIdAndTripId(it, tripId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        }
        val saved = tripRepository.saveAndFlush(trip)
        log.debug("여행 커버 변경 tripId={} ownerId={} photoId={}", tripId, ownerId, photoId)
        return detailOf(saved, ownerId)
    }

    /**
     * 여행을 기록과 함께 지운다. 사진은 바이너리만 지우고 메타데이터는 남긴다.
     *
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     */
    @Transactional
    fun delete(tripId: Long, ownerId: Long) {
        val trip = findEditableTrip(tripId, ownerId)

        tripShareRepository.deleteByTripId(tripId)
        trip.softDelete()
        recordRepository.softDeleteByTripId(tripId, Instant.now())
        val purged = photoDatabaseService.deleteByTrip(tripId)
        log.debug("여행 삭제 tripId={} ownerId={} purgedPhotoCount={}", tripId, ownerId, purged)
    }

    /**
     * 여행을 볼 수 있는지 확인한다.
     *
     * @param userId 비로그인이면 null
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     */
    fun requireViewable(tripId: Long, userId: Long?) {
        findViewableTrip(tripId, userId)
    }

    /**
     * 요청자가 소유한 여행을 읽는다. 기록을 담거나 옮길 때 쓴다.
     * 임의의 여행 id 로 존재를 떠볼 수 없도록 남의 여행도 404 로 답한다.
     *
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 요청자의 여행이 아닐 때
     */
    fun findOwnedTrip(tripId: Long, userId: Long): TripEntity =
        findTrip(tripId, userId, TripSpecifications.ownedBy(userId))

    /**
     * 공유 그룹을 통째로 바꾸고 이력을 남긴다. GROUP 이 아니면 공유를 모두 지운다.
     */
    private fun applyShares(
        trip: TripEntity,
        visibility: TripVisibility,
        groupIds: List<Long>,
        ownerId: Long,
    ) {
        val tripId = requireNotNull(trip.id)
        tripShareRepository.deleteByTripId(tripId)

        val groups = if (visibility == TripVisibility.GROUP) {
            groupService.requireAccessibleGroups(groupIds, ownerId)
        } else {
            emptyList()
        }
        tripShareRepository.saveAll(groups.map { TripShareEntity(trip = trip, group = it) })
        visibilityHistoryService.recordOwnerChange(trip, visibility, groups.mapNotNull { it.id })
    }

    /** 요청자가 볼 수 있는 여행을 읽는다. */
    private fun findViewableTrip(tripId: Long, userId: Long?): TripEntity =
        findTrip(tripId, userId, TripSpecifications.visibleTo(userId))

    /**
     * 요청자가 편집할 수 있는 여행을 읽는다.
     * 볼 수 없는 여행은 존재를 숨기도록 404, 볼 수 있지만 소유자가 아니면 403 으로 답한다.
     */
    private fun findEditableTrip(tripId: Long, userId: Long): TripEntity {
        val trip = findViewableTrip(tripId, userId)
        if (trip.owner.id != userId) {
            log.debug("여행 소유자 아님 tripId={} userId={} ownerId={}", tripId, userId, trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return trip
    }

    /** 접근 조건을 붙여 여행을 읽는다. 없는 것과 권한 없는 것을 구분하지 않는다. */
    private fun findTrip(tripId: Long, userId: Long?, access: Specification<TripEntity>): TripEntity =
        tripRepository.findOne(TripSpecifications.withId(tripId).and(access)).orElseThrow {
            log.debug("여행 조회 차단 tripId={} userId={}", tripId, userId)
            ApiException(ErrorCode.TRIP_NOT_FOUND)
        }

    /** 여행별 기록 수를 센다. 기록이 없는 여행은 결과에 없다. */
    private fun recordCounts(tripIds: Collection<Long>): Map<Long, Long> {
        if (tripIds.isEmpty()) return emptyMap()
        return recordRepository.countByTripIds(tripIds).associate { it.tripId to it.count }
    }

    /**
     * 여행별 공유 그룹을 읽는다.
     * 공유 대상은 소유자만 알아야 하므로 소유자의 여행만 넘긴다.
     */
    private fun sharesOf(trips: List<TripEntity>): Map<Long, List<GroupRef>> {
        val tripIds = trips.filter { it.visibility == TripVisibility.GROUP }.mapNotNull { it.id }
        if (tripIds.isEmpty()) return emptyMap()
        return tripShareRepository.findByTripIdIn(tripIds)
            .groupBy({ requireNotNull(it.trip.id) }) { GroupRef(requireNotNull(it.group.id), it.group.name) }
    }

    private fun TripEntity.coverUrl(): String? = coverPhoto?.let { photoDatabaseService.urlOf(requireNotNull(it.id)) }

    /** 여행 하나를 기록 수·공유 그룹과 함께 도메인 객체로 만든다. */
    private fun detailOf(trip: TripEntity, requesterId: Long?): Trip {
        val tripId = requireNotNull(trip.id)
        val shares = if (requesterId != null && trip.owner.id == requesterId) sharesOf(listOf(trip)) else emptyMap()
        return trip.toDomain(requesterId, recordCounts(listOf(tripId))[tripId] ?: 0L, shares)
    }

    /** 여행에 기록 수·커버 URL·공유 그룹을 붙여 도메인 객체로 만든다. */
    private fun TripEntity.toDomain(
        requesterId: Long?,
        recordCount: Long,
        shares: Map<Long, List<GroupRef>>,
    ): Trip {
        val tripId = requireNotNull(id)
        val mine = requesterId != null && owner.id == requesterId
        return Trip(
            id = tripId,
            name = name,
            startDate = startDate,
            endDate = endDate,
            headcount = headcount,
            budget = budget,
            memo = memo,
            coverPhotoUrl = coverUrl(),
            recordCount = recordCount,
            owner = owner.toUser(),
            visibility = if (mine) visibility else null,
            sharedGroups = if (mine && visibility == TripVisibility.GROUP) shares[tripId].orEmpty() else null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
