package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.service.GroupInviteService
import com.yong.travel.group.service.GroupService
import com.yong.travel.trip.domain.TripVisibility
import com.yong.travel.trip.presentation.TripCreateRequest
import com.yong.travel.trip.service.TripService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 권한 변경 이력이 남는지, 그룹을 나가면 공유가 풀리는지 확인한다.
 * 변경이 DB 에 반영된 뒤 읽도록 검증 전에 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class PermissionHistoryTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var groupInviteService: GroupInviteService

    @Test
    fun `탈퇴하면 그 사람이 그룹에 건 공유가 풀려 남은 멤버가 볼 수 없다`() {
        val owner = newUser()
        val member = newUser()
        val viewer = newUser()
        val groupId = newGroup(owner, member, viewer)
        val tripId = newTrip(member, TripVisibility.GROUP, listOf(groupId))
        assertEquals(tripId, tripService.get(tripId, viewer).id, "탈퇴 전에는 보인다")

        groupService.leave(groupId, member)
        flush()

        assertNotFound { tripService.get(tripId, viewer) }
        assertNotFound { tripService.get(tripId, owner) }
        assertEquals(listOf("GROUP|"), tripHistory(tripId).drop(1).map { it.state })
        assertEquals("OWNER_LEFT", tripHistory(tripId).last().cause)
    }

    @Test
    fun `제외되면 그 사람이 그룹에 건 공유만 풀리고 다른 그룹 공유는 남는다`() {
        val owner = newUser()
        val member = newUser()
        val viewer = newUser()
        val groupId = newGroup(owner, member, viewer)
        val otherGroupId = newGroup(member, viewer)
        val tripId = newTrip(member, TripVisibility.GROUP, listOf(groupId, otherGroupId))
        // 남의 공유는 건드리지 않아야 한다.
        val ownersTripId = newTrip(owner, TripVisibility.GROUP, listOf(groupId))

        groupService.removeMember(groupId, member, owner)
        flush()

        assertEquals(tripId, tripService.get(tripId, viewer).id, "남은 그룹으로는 계속 보인다")
        assertEquals(listOf(otherGroupId), tripService.get(tripId, member).sharedGroups?.map { it.id })
        assertNotFound { tripService.get(tripId, owner) }
        assertEquals(ownersTripId, tripService.get(ownersTripId, viewer).id)

        val last = tripHistory(tripId).last()
        assertEquals("OWNER_REMOVED", last.cause)
        assertEquals("GROUP|$otherGroupId", last.state)
        assertEquals(1, tripHistory(ownersTripId).size, "남의 여행에는 이력이 늘지 않는다")
    }

    @Test
    fun `소유자의 생성과 변경은 OWNER_CHANGE 로 남고 그룹 id 는 오름차순이다`() {
        val owner = newUser()
        val first = newGroup(owner)
        val second = newGroup(owner)
        val tripId = newTrip(owner)

        tripService.changeVisibility(tripId, owner, TripVisibility.GROUP, listOf(second, first))
        tripService.changeVisibility(tripId, owner, TripVisibility.PUBLIC, emptyList())
        // 값이 그대로여도 남긴다.
        tripService.changeVisibility(tripId, owner, TripVisibility.PUBLIC, emptyList())
        flush()

        val history = tripHistory(tripId)
        assertEquals(listOf("PRIVATE|", "GROUP|$first,$second", "PUBLIC|", "PUBLIC|"), history.map { it.state })
        assertEquals(setOf("OWNER_CHANGE"), history.map { it.cause }.toSet())
    }

    @Test
    fun `그룹을 지우면 공유된 여행마다 남은 그룹으로 이력이 남고 멤버 전원이 GROUP_DELETED 로 남는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroup(owner, member)
        val otherGroupId = newGroup(owner)
        val tripId = newTrip(owner, TripVisibility.GROUP, listOf(groupId, otherGroupId))
        val membersTripId = newTrip(member, TripVisibility.GROUP, listOf(groupId))

        groupService.delete(groupId, owner)
        flush()

        assertEquals(TripHistoryRow("GROUP|$otherGroupId", "GROUP_DELETED"), tripHistory(tripId).last())
        assertEquals(TripHistoryRow("GROUP|", "GROUP_DELETED"), tripHistory(membersTripId).last())
        // 그룹이 지워져도 이름은 남는다.
        assertEquals(
            setOf(MemberHistoryRow(owner, "가족", "GROUP_DELETED"), MemberHistoryRow(member, "가족", "GROUP_DELETED")),
            memberHistory(groupId).filter { it.event == "GROUP_DELETED" }.toSet(),
        )
    }

    @Test
    fun `그룹 생성과 초대 수락은 JOINED, 탈퇴와 제외는 LEFT 와 REMOVED 로 남는다`() {
        val owner = newUser()
        val leaver = newUser()
        val removed = newUser()
        val groupId = newGroup(owner, leaver, removed)

        groupService.leave(groupId, leaver)
        groupService.removeMember(groupId, removed, owner)
        flush()

        assertEquals(
            listOf(
                MemberHistoryRow(owner, "가족", "JOINED"),
                MemberHistoryRow(leaver, "가족", "JOINED"),
                MemberHistoryRow(removed, "가족", "JOINED"),
                MemberHistoryRow(leaver, "가족", "LEFT"),
                MemberHistoryRow(removed, "가족", "REMOVED"),
            ),
            memberHistory(groupId),
        )
    }

    @Test
    fun `공유가 없는 사람이 나가면 여행 이력은 늘지 않는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroup(owner, member)
        val tripId = newTrip(owner, TripVisibility.GROUP, listOf(groupId))

        groupService.leave(groupId, member)
        flush()

        assertEquals(1, tripHistory(tripId).size)
        assertEquals(tripId, tripService.get(tripId, owner).id)
    }

    /** 공개 범위와 공유 그룹을 `"GROUP|1,2"` 모양 한 문자열로 묶은 이력 행. */
    private data class TripHistoryRow(val state: String, val cause: String)

    private data class MemberHistoryRow(val userId: Long, val groupName: String, val event: String)

    private fun tripHistory(tripId: Long): List<TripHistoryRow> =
        entityManager.createNativeQuery(
            "select visibility || '|' || array_to_string(group_ids, ','), cause " +
                "from trip_visibility_history where trip_id = :tripId order by id",
        ).setParameter("tripId", tripId).resultList.map {
            val row = it as Array<*>
            TripHistoryRow(row[0] as String, row[1] as String)
        }

    private fun memberHistory(groupId: Long): List<MemberHistoryRow> =
        entityManager.createNativeQuery(
            "select user_id, group_name, event from group_member_history where group_id = :groupId order by id",
        ).setParameter("groupId", groupId).resultList.map {
            val row = it as Array<*>
            MemberHistoryRow((row[0] as Number).toLong(), row[1] as String, row[2] as String)
        }

    private fun assertNotFound(block: () -> Unit) {
        assertEquals(ErrorCode.TRIP_NOT_FOUND, assertThrows<ApiException> { block() }.errorCode)
    }

    /** 소유자와, 초대를 수락한 멤버들로 이루어진 그룹을 만든다. */
    private fun newGroup(ownerId: Long, vararg memberIds: Long): Long {
        val groupId = requireNotNull(groupService.create(ownerId, "가족", null).id)
        memberIds.forEach { memberId ->
            val email = requireNotNull(userRepository.findById(memberId).orElseThrow().email)
            groupInviteService.accept(groupInviteService.invite(groupId, ownerId, email).invite.id, memberId)
        }
        flush()
        return groupId
    }

    private fun newTrip(
        ownerId: Long,
        visibility: TripVisibility = TripVisibility.PRIVATE,
        groupIds: List<Long> = emptyList(),
    ): Long {
        val startDate = LocalDate.of(2026, 9, 5)
        val tripId = tripService.create(
            ownerId,
            TripCreateRequest(
                name = "테스트 여행",
                startDate = startDate,
                endDate = startDate.plusDays(3),
                headcount = 2,
                visibility = visibility,
                groupIds = groupIds,
            ).toCommand(),
        ).id
        flush()
        return tripId
    }

    private fun newUser(): Long = requireNotNull(
        userRepository.save(
            UserEntity(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = "tester-${System.nanoTime()}@example.com",
                name = "테스터",
            ),
        ).id,
    )

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
