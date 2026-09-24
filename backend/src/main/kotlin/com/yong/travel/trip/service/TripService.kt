package com.yong.travel.trip.service

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.persistence.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.persistence.TripRecordRepository
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripShareEntity
import com.yong.travel.trip.domain.TripDetail
import com.yong.travel.trip.domain.TripSummary
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.dto.TripCoverUpdateRequest
import com.yong.travel.trip.dto.TripCreateRequest
import com.yong.travel.trip.dto.TripListQuery
import com.yong.travel.trip.dto.TripSort
import com.yong.travel.trip.dto.TripUpdateRequest
import com.yong.travel.trip.dto.TripVisibilityUpdateRequest
import com.yong.travel.trip.persistence.TripRepository
import com.yong.travel.trip.persistence.TripShareRepository
import com.yong.travel.trip.persistence.TripSpecifications
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class TripService(
    private val tripRepository: TripRepository,
    private val tripShareRepository: TripShareRepository,
    private val recordRepository: TripRecordRepository,
    private val userRepository: UserRepository,
    private val groupService: GroupService,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(
        query: TripListQuery,
        userId: Long?,
        pageable: Pageable,
    ): PageResponse<TripSummary> {
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
        val summaries = page.content.map { it.toSummary(userId, counts, shares) }
        // 범위 판정이 쿼리 단계에 있어(명세 §7) 건수가 어긋나면 곧 유출이다. 개발 중 눈으로 확인할 값이다.
        log.debug("여행 목록 scope={} userId={} 건수={}", query.scope, userId, page.totalElements)
        return PageResponse(summaries, page.number, page.size, page.totalElements, page.totalPages)
    }

    /** 볼 권한이 없으면 없는 여행과 똑같이 TRIP_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(tripId: Long, userId: Long?): TripDetail {
        val trip = findTrip(tripId)
        requireViewable(trip, userId)
        return detailOf(trip, userId)
    }

    /** 단건 응답의 기록 수. 목록과 같은 집계 쿼리를 한 건짜리로 쓴다. */
    private fun recordCountOf(tripId: Long): Long = recordCounts(tripId)[tripId] ?: 0L

    @Transactional
    fun create(ownerId: Long, request: TripCreateRequest): TripDetail {
        val owner = userRepository.findById(ownerId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val trip = tripRepository.save(
            TripEntity(
                owner = owner,
                name = request.name.trim(),
                startDate = request.startDate,
                endDate = request.endDate,
                headcount = request.headcount,
                budget = request.budget,
                memo = request.memo,
                visibility = request.visibility,
            ),
        )
        applyShares(trip, request.visibility, request.groupIds, ownerId)
        log.debug("여행 생성 tripId={} ownerId={} visibility={}", trip.id, ownerId, trip.visibility)
        return detailOf(trip, ownerId)
    }

    /** 기본 정보만 바꾼다. 공개 범위는 changeVisibility 의 몫이다. */
    @Transactional
    fun update(tripId: Long, ownerId: Long, request: TripUpdateRequest): TripDetail {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        trip.name = request.name.trim()
        trip.startDate = request.startDate
        trip.endDate = request.endDate
        trip.headcount = request.headcount
        trip.budget = request.budget
        trip.memo = request.memo

        log.debug("여행 수정 tripId={} ownerId={}", tripId, ownerId)
        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    @Transactional
    fun changeVisibility(
        tripId: Long,
        ownerId: Long,
        request: TripVisibilityUpdateRequest,
    ): TripDetail {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        log.debug(
            "여행 공개 범위 변경 tripId={} ownerId={} {} -> {}",
            tripId, ownerId, trip.visibility, request.visibility,
        )
        trip.visibility = request.visibility
        applyShares(trip, request.visibility, request.groupIds, ownerId)
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    /**
     * 커버 사진 지정·해제. 그 여행의 하위 기록에 속한 사진만 지정할 수 있으며,
     * 아니면 PHOTO_NOT_FOUND 다 (존재 은닉, 명세 §4.3.2).
     */
    @Transactional
    fun changeCover(
        tripId: Long,
        ownerId: Long,
        request: TripCoverUpdateRequest,
    ): TripDetail {
        val trip = findTrip(tripId)
        requireOwner(trip, ownerId)

        trip.coverPhoto = request.photoId?.let {
            // 다른 여행의 사진인지 아예 없는 사진인지 구분되지 않아야 한다 (명세 §4.3.2).
            photoRepository.findByIdAndTripId(it, tripId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        }
        log.debug("여행 커버 변경 tripId={} ownerId={} photoId={}", tripId, ownerId, request.photoId)
        return detailOf(tripRepository.saveAndFlush(trip), ownerId)
    }

    /**
     * 여행 삭제. **하위 기록도 함께 soft delete 한다** — 기록은 여행 없이 존재할 수 없어
     * 남겨 둘 자리가 없다 (명세 §4.3).
     *
     * 공유 행은 물리 삭제한다. 여행이 조회에서 사라지므로 당장 새는 것은 아니지만, 남아 있는
     * 공유 행이 권한 판정에 끼어들 여지를 만들지 않는다 (명세 §3.1).
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
     * 되돌렸을 때 예전 공유가 의도치 않게 되살아난다 (명세 §4.3.1).
     */
    private fun applyShares(
        trip: TripEntity,
        visibility: Visibility,
        groupIds: List<Long>,
        ownerId: Long,
    ) {
        val tripId = requireNotNull(trip.id)
        tripShareRepository.deleteByTripId(tripId)
        // 삭제를 먼저 DB 에 반영한다. 그러지 않으면 Hibernate 가 INSERT 를 DELETE 보다 먼저
        // 내보내, 교체 후에도 남는 그룹에서 unique(trip_id, group_id) 위반이 난다.
        tripShareRepository.flush()
        if (visibility != Visibility.GROUP) return

        // 속하지 않은 그룹에는 공유할 수 없다. 그런 그룹 id 는 404 로 막아 존재 여부도 알리지 않는다.
        val groups = groupService.requireAccessibleGroups(groupIds, ownerId)
        tripShareRepository.saveAll(groups.map { TripShareEntity(trip = trip, group = it) })
    }

    private fun findTrip(tripId: Long): TripEntity =
        tripRepository.findById(tripId)
            .orElseThrow { ApiException(ErrorCode.TRIP_NOT_FOUND) }

    private fun requireViewable(trip: TripEntity, userId: Long?) {
        if (canView(trip, userId)) return
        // 존재 은닉 때문에 응답이 "없음"과 같다. 왜 가려졌는지는 이 로그에만 드러난다 (명세 §2.2).
        log.debug("여행 열람 차단 tripId={} userId={} visibility={}", trip.id, userId, trip.visibility)
        // 권한 없음(403)이 아니라 없는 여행(404)으로 답한다. 403 은 "그 여행이 있다" 는 뜻이 된다.
        throw ApiException(ErrorCode.TRIP_NOT_FOUND)
    }

    /** 내 여행이거나, 전체 공개이거나, 내가 속한 그룹으로 공유됐거나 셋 중 하나다 (명세 §2.2). */
    private fun canView(trip: TripEntity, userId: Long?): Boolean {
        if (userId != null && trip.owner.id == userId) return true
        if (trip.visibility == Visibility.PUBLIC) return true
        if (trip.visibility != Visibility.GROUP || userId == null) return false

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
     * (공통 명세 §3.5), 응답에서 가리는 대신 애초에 읽지 않는다.
     */
    private fun sharesOf(trips: List<TripEntity>): Map<Long, List<Group>> {
        val tripIds = trips.filter { it.visibility == Visibility.GROUP }.mapNotNull { it.id }
        if (tripIds.isEmpty()) return emptyMap()
        return tripShareRepository.findByTripIdIn(tripIds)
            .groupBy({ requireNotNull(it.trip.id) }) { Group(requireNotNull(it.group.id), it.group.name) }
    }

    private fun TripEntity.coverUrl(): String? = coverPhoto?.let { photoStorageService.resolveUrl(it.storageKey) }

    private fun TripEntity.toOwner() = User(requireNotNull(owner.id), owner.name, owner.profileImageUrl)

    /**
     * 여행 엔티티에 기록 수·커버 URL·공유 그룹을 붙여 [TripDetail] 로 만든다.
     *
     * 조회가 여기서 끝난다 — 응답을 만들면서 리포지토리를 다시 부르지 않기 위해 도메인 객체를
     * 두는 것이므로, 이 함수를 지나면 더 읽을 것이 없어야 한다.
     */
    private fun detailOf(trip: TripEntity, requesterId: Long?): TripDetail {
        val tripId = requireNotNull(trip.id)
        val mine = requesterId != null && trip.owner.id == requesterId
        return TripDetail(
            id = tripId,
            name = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            headcount = trip.headcount,
            budget = trip.budget,
            memo = trip.memo,
            coverPhotoUrl = trip.coverUrl(),
            recordCount = recordCountOf(tripId),
            owner = trip.toOwner(),
            visibility = if (mine) trip.visibility else null,
            sharedGroups = if (mine && trip.visibility == Visibility.GROUP) {
                sharesOf(listOf(trip))[tripId].orEmpty()
            } else {
                null
            },
            createdAt = trip.createdAt,
            updatedAt = trip.updatedAt,
        )
    }

    /** 목록 한 줄. 기록 수와 공유 그룹은 호출부가 한 번에 모아 온 것을 받는다. */
    private fun TripEntity.toSummary(
        requesterId: Long?,
        counts: Map<Long, Long>,
        shares: Map<Long, List<Group>>,
    ): TripSummary {
        val tripId = requireNotNull(id)
        val mine = requesterId != null && owner.id == requesterId
        return TripSummary(
            id = tripId,
            name = name,
            startDate = startDate,
            endDate = endDate,
            headcount = headcount,
            budget = budget,
            coverPhotoUrl = coverUrl(),
            recordCount = counts[tripId] ?: 0L,
            owner = toOwner(),
            visibility = if (mine) visibility else null,
            sharedGroups = if (mine && visibility == Visibility.GROUP) shares[tripId].orEmpty() else null,
            createdAt = createdAt,
        )
    }
}
