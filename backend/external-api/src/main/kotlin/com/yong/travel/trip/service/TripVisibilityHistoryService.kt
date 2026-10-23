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
 * 여행 공유 관계가 바뀔 때 바뀐 뒤의 상태를 이력으로 남긴다.
 *
 * 공유를 바꾸는 경로는 여행 쪽(생성·공개 범위 변경)과 그룹 쪽(그룹 삭제·탈퇴·제외)으로 나뉜다.
 * 그룹 쪽 경로가 공유 행을 직접 지우면 이력이 조용히 빠지므로, 공유를 푸는 일도 여기서만 한다.
 * TripService 가 GroupService 에 의존하므로 그룹 쪽이 TripService 를 부르면 순환 의존이 되어 따로 둔다.
 *
 * 부르는 쪽의 트랜잭션 안에서만 돈다 — 이력을 남기지 못하면 권한 변경도 함께 롤백되어야 한다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class TripVisibilityHistoryService(
    private val tripShareRepository: TripShareRepository,
    private val historyRepository: TripVisibilityHistoryRepository,
) {

    /**
     * 소유자가 정한 상태를 남긴다. 값이 그대로여도 남긴다 — 상태 전체를 적는 행이라 되짚는 데 해가 없다.
     *
     * @param groupIds 바뀐 뒤의 공유 그룹 id. GROUP 이 아니면 빈 목록
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

    /** 그룹 삭제로 그 그룹에 걸린 공유를 모두 풀고, 영향받은 여행마다 이력을 남긴다. */
    fun revokeGroupShares(groupId: Long): Int =
        revoke(tripShareRepository.findByGroupId(groupId), TripVisibilityChangeCause.GROUP_DELETED)

    /**
     * 멤버가 그룹을 나갈 때 그 사람이 이 그룹에 걸어 둔 공유만 푼다.
     *
     * 여행은 소유자가 속한 그룹에만 공유할 수 있으므로, 나간 뒤에도 공유가 남으면 그룹 밖 사람이
     * 그룹 안에 여행을 계속 보여 주는 상태가 된다.
     */
    fun revokeOwnerShares(groupId: Long, ownerId: Long, cause: TripVisibilityChangeCause): Int =
        revoke(tripShareRepository.findByGroupIdAndTripOwnerId(groupId, ownerId), cause)

    /**
     * 공유 행을 지운 뒤 남은 공유 그룹으로 이력을 만든다.
     *
     * 공개 범위는 GROUP 그대로 둔다. 남은 그룹이 없으면 판정상 PRIVATE 과 같다.
     */
    private fun revoke(shares: List<TripShareEntity>, cause: TripVisibilityChangeCause): Int {
        if (shares.isEmpty()) return 0
        tripShareRepository.deleteAllInBatch(shares)

        val trips = shares.map { it.trip }.distinctBy { it.id }
        // 지운 뒤에 읽어야 남은 그룹만 담긴다. 일괄 삭제는 호출 즉시 DB 에 반영된다.
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
