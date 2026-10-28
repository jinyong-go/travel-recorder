package com.yong.travel.trip.service

import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.domain.TripVisibilityChangeCause
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripShareEntity
import com.yong.travel.trip.persistence.TripShareRepository
import com.yong.travel.trip.persistence.TripVisibilityHistoryEntity
import com.yong.travel.trip.persistence.TripVisibilityHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 여행 공유가 바뀔 때 바뀐 뒤의 상태를 이력으로 남긴다.
 * 이력이 빠지지 않도록 그룹 쪽에서 공유를 푸는 일도 여기서 한다.
 * 이력을 못 남기면 공유 변경도 되돌려야 하므로 호출자 트랜잭션 안에서만 돈다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class TripVisibilityHistoryService(
    private val tripShareRepository: TripShareRepository,
    private val historyRepository: TripVisibilityHistoryRepository,
) {

    /**
     * 소유자가 정한 공개 범위를 남긴다. 값이 그대로여도 남긴다.
     *
     * @param groupIds GROUP 이 아니면 빈 목록
     */
    fun recordOwnerChange(trip: TripEntity, visibility: TripVisibility, groupIds: Collection<Long>) {
        historyRepository.save(
            TripVisibilityHistoryEntity(
                trip = trip,
                visibility = visibility,
                groupIds = groupIds.sorted(),
                cause = TripVisibilityChangeCause.OWNER_CHANGE,
            ),
        )
    }

    /** 그룹에 걸린 공유를 모두 풀고 이력을 남긴다. */
    fun revokeGroupShares(groupId: Long): Int =
        revoke(tripShareRepository.findByGroupId(groupId), TripVisibilityChangeCause.GROUP_DELETED)

    /**
     * 그룹을 나간 사람이 그 그룹에 건 공유를 푼다.
     * 여행은 속한 그룹에만 공유할 수 있으므로 나가면 공유도 끝나야 한다.
     */
    fun revokeOwnerShares(groupId: Long, ownerId: Long, cause: TripVisibilityChangeCause): Int =
        revoke(tripShareRepository.findByGroupIdAndTripOwnerId(groupId, ownerId), cause)

    /**
     * 공유를 지우고 남은 공유 그룹으로 이력을 남긴다.
     * 공개 범위는 GROUP 그대로 둔다. 남은 그룹이 없으면 비공개와 같다.
     */
    private fun revoke(shares: List<TripShareEntity>, cause: TripVisibilityChangeCause): Int {
        if (shares.isEmpty()) return 0
        tripShareRepository.deleteAllInBatch(shares)

        val trips = shares.map { it.trip }.distinctBy { it.id }
        // 남은 그룹만 담기도록 지운 뒤에 읽는다.
        val remaining = tripShareRepository.findByTripIdIn(trips.mapNotNull { it.id })
            .groupBy({ requireNotNull(it.trip.id) }, { requireNotNull(it.group.id) })
        historyRepository.saveAll(
            trips.map {
                TripVisibilityHistoryEntity(
                    trip = it,
                    visibility = TripVisibility.GROUP,
                    groupIds = remaining[it.id].orEmpty().sorted(),
                    cause = cause,
                )
            },
        )
        return shares.size
    }
}
