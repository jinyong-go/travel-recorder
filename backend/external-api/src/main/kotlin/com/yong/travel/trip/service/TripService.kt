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

/**
 * 여행의 조회·생성·수정·삭제와 공개 범위·커버 지정.
 *
 * 열람 판정은 조회 조건으로 한다. 판정식은 TripVisibilityPredicates 하나뿐이고 메모리 판정은 두지 않는다.
 */
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

    /**
     * 요청자가 볼 수 있는 여행 중 `scope` 에 해당하는 것을 페이지로 돌려준다.
     *
     * 범위 판정은 조회 쿼리 단계에서 한다. 기록 수와 (소유자 본인 항목의) 공유 그룹은 페이지 전체를
     * 한 번에 모아 붙인다.
     *
     * @param query 범위·검색어·정렬
     * @param userId 요청자의 사용자 id. 비로그인이면 null 이며 PUBLIC 만 조회된다
     * @param page 0부터 시작하는 페이지 번호
     * @return 여행 한 페이지
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
            // 시작일이 같은 여행끼리는 순서가 뒤집히지 않도록 생성 시각을 보조 키로 둔다.
            TripSort.START_DATE ->
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val trips = tripRepository.findAll(spec, listPageRequest(page, sort))

        val counts = recordCounts(*trips.content.mapNotNull { it.id }.toLongArray())
        // 공유 그룹은 소유자 본인의 항목에만 필요하다. 그 여행들만 골라 한 번에 읽는다.
        val shares = sharesOf(trips.content.filter { userId != null && it.owner.id == userId })
        // 범위 판정이 쿼리 단계에 있어 건수가 어긋나면 곧 유출이다. 개발 중 눈으로 확인할 값이다.
        log.debug("여행 목록 scope={} userId={} count={}", query.scope, userId, trips.totalElements)
        return trips.toPageResult().map { it.toDomain(userId, counts[requireNotNull(it.id)] ?: 0L, shares) }
    }

    /**
     * 여행 상세를 조회한다.
     *
     * @param tripId 조회할 여행 id
     * @param userId 요청자의 사용자 id. 비로그인이면 null
     * @return 여행 상세. 공개 범위와 공유 그룹은 소유자에게만 채워진다
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때 (존재 은닉)
     */
    fun get(tripId: Long, userId: Long?): Trip = detailOf(findViewableTrip(tripId, userId), userId)

    /**
     * 여행을 만든다. 소유자는 요청자로 고정되며, GROUP 이면 공유 그룹도 함께 건다.
     *
     * @param ownerId 요청자의 사용자 id. 그대로 소유자가 된다
     * @param command 여행 입력값
     * @return 만들어진 여행 상세
     * @throws ApiException `UNAUTHENTICATED` — 세션은 살아 있는데 사용자 행이 없을 때
     * @throws ApiException `GROUP_NOT_FOUND` — 공유 그룹에 요청자가 속하지 않은 그룹이 섞였을 때
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
     * 여행의 기본 정보를 바꾼다. 공개 범위는 [changeVisibility] 의 몫이다.
     *
     * @param tripId 수정할 여행 id
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @param command 새 기본 정보
     * @return 수정된 여행 상세
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
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

        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        val saved = tripRepository.saveAndFlush(trip)
        log.debug("여행 수정 tripId={} ownerId={}", tripId, ownerId)
        return detailOf(saved, ownerId)
    }

    /**
     * 공개 범위를 바꾼다.
     *
     * @param tripId 대상 여행 id
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @param visibility 새 공개 범위
     * @param groupIds 공유 그룹 id 목록. GROUP 일 때만 쓰이며 기존 공유 그룹을 통째로 대체한다
     * @return 수정된 여행 상세
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 섞였을 때
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
     * 커버 사진을 지정하거나 해제한다. 그 여행의 하위 기록에 속한 사진만 지정할 수 있다.
     *
     * @param tripId 대상 여행 id
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @param photoId 커버로 지정할 사진 id. null 이면 해제한다
     * @return 수정된 여행 상세
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 이 여행의 사진이 아닐 때 (존재 은닉)
     */
    @Transactional
    fun changeCover(
        tripId: Long,
        ownerId: Long,
        photoId: UUID?,
    ): Trip {
        val trip = findEditableTrip(tripId, ownerId)

        trip.coverPhoto = photoId?.let {
            // 다른 여행의 사진인지 아예 없는 사진인지 구분되지 않아야 한다.
            photoRepository.findByIdAndTripId(it, tripId) ?: throw ApiException(ErrorCode.PHOTO_NOT_FOUND)
        }
        val saved = tripRepository.saveAndFlush(trip)
        log.debug("여행 커버 변경 tripId={} ownerId={} photoId={}", tripId, ownerId, photoId)
        return detailOf(saved, ownerId)
    }

    /**
     * 여행 삭제. **하위 기록도 함께 soft delete 한다** — 기록은 여행 없이 존재할 수 없어
     * 남겨 둘 자리가 없다.
     *
     * 공유 행은 물리 삭제한다. 여행이 조회에서 사라지므로 당장 새는 것은 아니지만, 남아 있는
     * 공유 행이 권한 판정에 끼어들 여지를 만들지 않는다.
     *
     * 하위 기록의 사진은 메타데이터를 남기고 바이너리를 지운다. 기록 하나를 지울 때와 같은 이유다.
     *
     * @param tripId 삭제할 여행 id
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     */
    @Transactional
    fun delete(tripId: Long, ownerId: Long) {
        val trip = findEditableTrip(tripId, ownerId)

        tripShareRepository.deleteByTripId(tripId)

        // 여행을 먼저 지우고 flush 한다. 하위 기록의 벌크 update 가 영속성 컨텍스트를 비우므로
        // (clearAutomatically), 순서를 바꾸면 trip 이 준영속 상태가 되어 삭제가 유실된다.
        trip.softDelete()
        tripRepository.saveAndFlush(trip)

        recordRepository.softDeleteByTripId(tripId, Instant.now())
        // 하위 기록의 사진 바이너리도 지운다. 메타데이터는 기록과 함께 남는다.
        val purged = photoDatabaseService.deleteByTrip(tripId)
        log.debug("여행 삭제 tripId={} ownerId={} photos={}", tripId, ownerId, purged)
    }

    /**
     * 여행을 볼 수 있는지 판정만 한다. 못 보면 없는 여행과 같은 TRIP_NOT_FOUND 다.
     *
     * 기록 목록을 `tripId` 로 좁힐 때 기록 서비스가 부른다. 여행 상세 조회와 같은
     * 판정을 거쳐야 목록으로 우회해 여행의 존재를 떠볼 수 없다.
     *
     * @param tripId 판정할 여행 id
     * @param userId 요청자의 사용자 id. 비로그인이면 null
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     */
    fun requireViewable(tripId: Long, userId: Long?) {
        findViewableTrip(tripId, userId)
    }

    /**
     * 기록을 담거나 옮길 대상 여행을 읽는다. 요청자가 소유한 여행이어야 한다.
     *
     * 편집 관문([findEditableTrip])과 달리 볼 수 있는 남의 여행이어도 403 이 아니라 404 다.
     * 이 경로는 임의의 여행 id 를 넣어볼 수 있어, 403 으로 답하면 그 여행의 존재가 드러난다.
     *
     * 엔티티를 반환한다. 컨트롤러로 나가는 경계가 아니라 기록 서비스가 연관을 걸 때 쓰는 서비스 간 호출이다.
     *
     * @param tripId 대상 여행 id
     * @param userId 요청자의 사용자 id
     * @return 요청자의 여행
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 요청자의 여행이 아닐 때
     */
    fun findOwnedTrip(tripId: Long, userId: Long): TripEntity =
        findTrip(tripId, userId, TripSpecifications.ownedBy(userId))

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

    /** 요청자가 볼 수 있는 여행을 읽는다. 없거나 볼 수 없으면 TRIP_NOT_FOUND 다. */
    private fun findViewableTrip(tripId: Long, userId: Long?): TripEntity =
        findTrip(tripId, userId, TripSpecifications.visibleTo(userId))

    /** 소유자만 통과시킨다. 볼 수도 없는 여행이면 404, 볼 수 있는데 소유자가 아니면 403 이다. */
    private fun findEditableTrip(tripId: Long, userId: Long): TripEntity {
        // 볼 수도 없는 여행이면 존재부터 숨긴다. 존재를 이미 아는 사람에게만 403 으로 답한다.
        val trip = findViewableTrip(tripId, userId)
        if (trip.owner.id != userId) {
            log.debug("여행 소유자 아님 tripId={} userId={} ownerId={}", tripId, userId, trip.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return trip
    }

    /**
     * 판정 조건을 붙여 여행 하나를 읽는다.
     *
     * 판정이 조회 조건에 실려 있어 "없음"과 "권한 없음"이 같은 빈 결과로 나온다. 응답이 구분되지
     * 않는 것이 존재 은닉 그 자체다.
     */
    private fun findTrip(tripId: Long, userId: Long?, access: Specification<TripEntity>): TripEntity =
        tripRepository.findOne(TripSpecifications.withId(tripId).and(access)).orElseThrow {
            // 응답이 "없음"과 같으므로 막혔을 수 있다는 사실은 이 로그에만 남는다.
            log.debug("여행 조회 차단 tripId={} userId={}", tripId, userId)
            ApiException(ErrorCode.TRIP_NOT_FOUND)
        }

    /** 단건 응답의 기록 수. 목록과 같은 집계 쿼리를 한 건짜리로 쓴다. */
    private fun recordCountOf(tripId: Long): Long = recordCounts(tripId)[tripId] ?: 0L

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

    /** 커버 사진의 접근 URL. 커버가 없으면 null 이다. */
    private fun TripEntity.coverUrl(): String? = coverPhoto?.let { photoDatabaseService.urlOf(requireNotNull(it.id)) }

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
            owner = owner.toUser(),
            visibility = if (mine) visibility else null,
            sharedGroups = if (mine && visibility == TripVisibility.GROUP) shares[tripId].orEmpty() else null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
