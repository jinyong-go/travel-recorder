package com.yong.travel.trip.service

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoDatabaseService
import com.yong.travel.record.persistence.TripRecordRepository
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripShareEntity
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.domain.TripCreateCommand
import com.yong.travel.trip.domain.TripListQuery
import com.yong.travel.trip.domain.TripSort
import com.yong.travel.trip.domain.TripUpdateCommand
import com.yong.travel.trip.persistence.TripRepository
import com.yong.travel.trip.persistence.TripShareRepository
import com.yong.travel.trip.persistence.TripSpecifications
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(
        query: TripListQuery,
        userId: Long?,
        pageable: Pageable,
    ): Page<Trip> {
        val groupIds = groupService.groupIdsOf(userId)
        val spec = TripSpecifications.withScope(query.scope, userId, groupIds)
            .and(TripSpecifications.withKeyword(query.keyword))

        val sort = when (query.sort) {
            TripSort.RECENT -> Sort.by(Sort.Direction.DESC, "createdAt")
            // 시작일이 같은 여행끼리는 순서가 뒤집히지 않도록 생성 시각을 보조 키로 둔다.
            TripSort.START_DATE ->
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val page = tripRepository.findAll(spec, PageRequest.of(pageable.pageNumber, pageable.pageSize, sort))

        val counts = recordCounts(*page.content.mapNotNull { it.id }.toLongArray())
        // 공유 그룹은 소유자 본인의 항목에만 필요하다. 그 여행들만 골라 한 번에 읽는다.
        val shares = sharesOf(page.content.filter { userId != null && it.owner.id == userId })
        // 범위 판정이 쿼리 단계에 있어 건수가 어긋나면 곧 유출이다. 개발 중 눈으로 확인할 값이다.
        log.debug("여행 목록 scope={} userId={} 건수={}", query.scope, userId, page.totalElements)
        return page.map { it.toDomain(userId, counts[requireNotNull(it.id)] ?: 0L, shares) }
    }

    /** 볼 권한이 없으면 없는 여행과 똑같이 TRIP_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(tripId: Long, userId: Long?): Trip {
        val trip = findTrip(tripId)
        requireViewable(trip, userId)
        return detailOf(trip, userId)
    }

    /** 단건 응답의 기록 수. 목록과 같은 집계 쿼리를 한 건짜리로 쓴다. */
    private fun recordCountOf(tripId: Long): Long = recordCounts(tripId)[tripId] ?: 0L

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

    /** 기본 정보만 바꾼다. 공개 범위는 changeVisibility 의 몫이다. */
    @Transactional
    fun update(tripId: Long, ownerId: Long, command: TripUpdateCommand): Trip {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        trip.name = command.name.trim()
        trip.startDate = command.startDate
        trip.endDate = command.endDate
        trip.headcount = command.headcount
        trip.budget = command.budget
        trip.memo = command.memo

        log.debug("여행 수정 tripId={} ownerId={}", tripId, ownerId)
        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    /** 공개 범위 변경. `groupIds` 는 GROUP 일 때만 쓰이며 기존 공유 그룹을 통째로 대체한다. */
    @Transactional
    fun changeVisibility(
        tripId: Long,
        ownerId: Long,
        visibility: TripVisibility,
        groupIds: List<Long>,
    ): Trip {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        log.debug(
            "여행 공개 범위 변경 tripId={} ownerId={} {} -> {}",
            tripId, ownerId, trip.visibility, visibility,
        )
        trip.visibility = visibility
        applyShares(trip, visibility, groupIds, ownerId)
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    /**
     * 커버 사진 지정·해제. `photoId` 가 null 이면 해제한다.
     * 그 여행의 하위 기록에 속한 사진만 지정할 수 있으며, 아니면 PHOTO_NOT_FOUND 다 (존재 은닉).
     */
    @Transactional
    fun changeCover(
        tripId: Long,
        ownerId: Long,
        photoId: UUID?,
    ): Trip {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        trip.coverPhoto = photoId?.let {
            // 다른 여행의 사진인지 아예 없는 사진인지 구분되지 않아야 한다.
            photoRepository.findByIdAndTripId(it, tripId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        }
        log.debug("여행 커버 변경 tripId={} ownerId={} photoId={}", tripId, ownerId, photoId)
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    /**
     * 여행 삭제. **하위 기록도 함께 soft delete 한다** — 기록은 여행 없이 존재할 수 없어
     * 남겨 둘 자리가 없다.
     *
     * 공유 행은 물리 삭제한다. 여행이 조회에서 사라지므로 당장 새는 것은 아니지만, 남아 있는
     * 공유 행이 권한 판정에 끼어들 여지를 만들지 않는다.
     */
    @Transactional
    fun delete(tripId: Long, ownerId: Long) {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        tripShareRepository.deleteByTripId(tripId)

        // 여행을 먼저 지우고 flush 한다. 하위 기록의 벌크 update 가 영속성 컨텍스트를 비우므로
        // (clearAutomatically), 순서를 바꾸면 trip 이 준영속 상태가 되어 삭제가 유실된다.
        trip.softDelete()
        tripRepository.saveAndFlush(trip)

        recordRepository.softDeleteByTripId(tripId, Instant.now())
        log.debug("여행 삭제 tripId={} ownerId={} (하위 기록 함께 soft delete)", tripId, ownerId)
    }

    /**
     * 공유 그룹 목록을 통째로 갈아 끼운다.
     *
     * GROUP 이 아닌 값으로 바뀌면 기존 공유를 지운다 — 남겨 두면 나중에 다시 GROUP 으로
     * 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
     */
    private fun applyShares(
        trip: TripEntity,
        visibility: TripVisibility,
        groupIds: List<Long>,
        ownerId: Long,
    ) {
        val tripId = requireNotNull(trip.id)
        // 벌크 삭제라 호출 즉시 DB 에 반영된다. 아래 INSERT 보다 늦게 나가 교체 후에도 남는 그룹에서
        // unique(trip_id, group_id) 위반이 나는 일이 없다.
        tripShareRepository.deleteByTripId(tripId)
        if (visibility != TripVisibility.GROUP) return

        // 속하지 않은 그룹에는 공유할 수 없다. 그런 그룹 id 는 404 로 막아 존재 여부도 알리지 않는다.
        val groups = groupService.requireAccessibleGroups(groupIds, ownerId)
        tripShareRepository.saveAll(groups.map { TripShareEntity(trip = trip, group = it) })
    }

    private fun findTrip(tripId: Long): TripEntity =
        tripRepository.findById(tripId)
            .orElseThrow { ApiException(ErrorCode.TRIP_NOT_FOUND) }

    /**
     * 여행을 볼 수 있는지 판정만 한다. 못 보면 없는 여행과 같은 TRIP_NOT_FOUND 다.
     *
     * 기록 목록을 `tripId` 로 좁힐 때 기록 서비스가 부른다. 여행 상세 조회와 같은
     * 판정을 거쳐야 목록으로 우회해 여행의 존재를 떠볼 수 없다.
     */
    fun requireViewable(tripId: Long, userId: Long?) {
        requireViewable(findTrip(tripId), userId)
    }

    private fun requireViewable(trip: TripEntity, userId: Long?) {
        if (canView(trip, userId)) return
        // 존재 은닉 때문에 응답이 "없음"과 같다. 왜 가려졌는지는 이 로그에만 드러난다.
        log.debug("여행 열람 차단 tripId={} userId={} visibility={}", trip.id, userId, trip.visibility)
        // 권한 없음(403)이 아니라 없는 여행(404)으로 답한다. 403 은 "그 여행이 있다" 는 뜻이 된다.
        throw ApiException(ErrorCode.TRIP_NOT_FOUND)
    }

    /**
     * 내 여행이거나, 전체 공개이거나, 내가 속한 그룹으로 공유됐거나 셋 중 하나다.
     *
     * 기록의 열람도 전적으로 소속 여행이 정하므로 기록 서비스가 이 판정을 그대로 쓴다.
     * 판정식이 두 곳에 있으면 한쪽만 고쳐졌을 때 여행과 기록의 공개 범위가 어긋난다.
     */
    fun canView(trip: TripEntity, userId: Long?): Boolean {
        if (userId != null && trip.owner.id == userId) return true
        if (trip.visibility == TripVisibility.PUBLIC) return true
        if (trip.visibility != TripVisibility.GROUP || userId == null) return false

        val groupIds = groupService.groupIdsOf(userId)
        return groupIds.isNotEmpty() &&
            tripShareRepository.existsByTripIdAndGroupIdIn(requireNotNull(trip.id), groupIds)
    }

    private fun requireOwner(trip: TripEntity, userId: Long) {
        // 볼 수도 없는 여행이면 존재부터 숨긴다. 볼 수 있는데 소유자가 아닌 경우에만 403 이다.
        requireViewable(trip, userId)
        if (trip.owner.id != userId) {
            log.debug("여행 소유자 아님 tripId={} userId={} ownerId={}", trip.id, userId, trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 여행별 기록 수를 한 번에 센다. 기록이 없는 여행은 결과에 없으므로 호출부가 0 으로 읽는다. */
    private fun recordCounts(vararg tripIds: Long): Map<Long, Long> {
        if (tripIds.isEmpty()) return emptyMap()
        return recordRepository.countByTripIds(tripIds.toList())
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
    }

    /**
     * 여행별 공유 그룹을 한 번에 읽는다.
     *
     * **소유자 본인의 여행만 넘겨야 한다.** 누구에게 공유했는지는 소유자만 아는 정보라
     * 응답에서 가리는 대신 애초에 읽지 않는다.
     */
    private fun sharesOf(trips: List<TripEntity>): Map<Long, List<GroupRef>> {
        val tripIds = trips.filter { it.visibility == TripVisibility.GROUP }.mapNotNull { it.id }
        if (tripIds.isEmpty()) return emptyMap()
        return tripShareRepository.findByTripIdIn(tripIds)
            .groupBy({ requireNotNull(it.trip.id) }) { GroupRef(requireNotNull(it.group.id), it.group.name) }
    }

    private fun TripEntity.coverUrl(): String? = coverPhoto?.let { photoDatabaseService.urlOf(requireNotNull(it.id)) }

    private fun TripEntity.toOwner() = User(requireNotNull(owner.id), owner.name, owner.profileImageUrl)

    /** 단건 응답. 기록 수와 (소유자라면) 공유 그룹을 이 여행 하나에 대해 읽어 [toDomain] 에 넘긴다. */
    private fun detailOf(trip: TripEntity, requesterId: Long?): Trip {
        val tripId = requireNotNull(trip.id)
        val shares = if (requesterId != null && trip.owner.id == requesterId) sharesOf(listOf(trip)) else emptyMap()
        return trip.toDomain(requesterId, recordCountOf(tripId), shares)
    }

    /**
     * 여행 엔티티에 기록 수·커버 URL·공유 그룹을 붙여 [Trip] 으로 만든다. 목록과 단건이 함께 쓴다.
     *
     * 기록 수와 공유 그룹은 호출부가 미리 모아 온다 — 목록에서 항목마다 조회하지 않기 위해서다.
     * 조회가 여기서 끝난다. 응답을 만들면서 리포지토리를 다시 부르지 않기 위해 도메인 객체를
     * 두는 것이므로, 이 함수를 지나면 더 읽을 것이 없어야 한다.
     */
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
            owner = toOwner(),
            visibility = if (mine) visibility else null,
            sharedGroups = if (mine && visibility == TripVisibility.GROUP) shares[tripId].orEmpty() else null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
