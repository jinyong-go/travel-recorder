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
 * 그룹을 나가거나 그룹이 지워지면 여행 공유가 풀리는지 확인한다.
 * 변경이 DB 에 반영된 뒤 읽도록 검증 전에 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class GroupShareRevokeTest {

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
    }

    @Test
    fun `그룹을 지우면 그 그룹 공유만 풀리고 다른 그룹 공유는 남는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroup(owner, member)
        val otherGroupId = newGroup(owner)
        val tripId = newTrip(owner, TripVisibility.GROUP, listOf(groupId, otherGroupId))
        val membersTripId = newTrip(member, TripVisibility.GROUP, listOf(groupId))

        groupService.delete(groupId, owner)
        flush()

        assertEquals(listOf(otherGroupId), tripService.get(tripId, owner).sharedGroups?.map { it.id })
        assertEquals(emptyList(), tripService.get(membersTripId, member).sharedGroups?.map { it.id })
        assertNotFound { tripService.get(membersTripId, owner) }
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
