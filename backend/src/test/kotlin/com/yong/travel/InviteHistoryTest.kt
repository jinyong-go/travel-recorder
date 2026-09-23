package com.yong.travel

import com.yong.travel.auth.persistence.User
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.web.DEFAULT_PAGE_SIZE
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.dto.GroupCreateRequest
import com.yong.travel.group.dto.InviteHistoryRole
import com.yong.travel.group.dto.InviteRequest
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 끝난 초대가 이력으로 남는지 검증 (공통 명세 §3.7).
 *
 * 이력을 남기는 지점이 넷(수락·거절·취소·그룹 삭제)이라 **한 곳만 빠져도 조용히 비는** 자리다.
 * 조회 시점에는 "원래 없었던 것"과 구분되지 않으므로 네 경로를 각각 고정한다.
 *
 * 1차 캐시 때문에 변경 직후 조회가 DB 를 타지 않으므로 검증 전에 flush/clear 로 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class InviteHistoryTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var inviteService: InviteService

    @Test
    fun `수락하면 멤버가 되고 이력에 ACCEPTED 로 남는다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val groupId = newGroup(owner)
        val inviteId = invite(groupId, owner, "invitee@example.com")

        inviteService.accept(inviteId, invitee)
        flush()

        val history = historyOf(invitee, InviteHistoryRole.RECEIVED)
        assertEquals(1, history.size)
        assertEquals(InviteOutcome.ACCEPTED, history.single().outcome)
        // 대기 목록에서는 사라진다.
        assertEquals(0, inviteService.listReceived(invitee, page()).totalElements.toInt())
    }

    @Test
    fun `거절하면 이력에 REJECTED 로 남고 보낸 사람도 본다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val groupId = newGroup(owner)
        val inviteId = invite(groupId, owner, "invitee@example.com")

        inviteService.reject(inviteId, invitee)
        flush()

        // 거절을 감추지 않는 것이 이번 개정의 결정이다 (공통 명세 §3.7).
        val sent = historyOf(owner, InviteHistoryRole.SENT)
        assertEquals(InviteOutcome.REJECTED, sent.single().outcome)
        assertEquals(InviteOutcome.REJECTED, historyOf(invitee, InviteHistoryRole.RECEIVED).single().outcome)
    }

    @Test
    fun `취소하면 이력에 REVOKED 로 남는다`() {
        val owner = newUser()
        newUser("invitee@example.com")
        val groupId = newGroup(owner)
        val inviteId = invite(groupId, owner, "invitee@example.com")

        inviteService.revoke(groupId, inviteId, owner)
        flush()

        assertEquals(InviteOutcome.REVOKED, historyOf(owner, InviteHistoryRole.SENT).single().outcome)
    }

    @Test
    fun `그룹을 지우면 대기 중이던 초대가 GROUP_DELETED 로 남고 그룹명 스냅샷이 보존된다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val groupId = newGroup(owner)
        invite(groupId, owner, "invitee@example.com")

        groupService.delete(groupId, owner)
        flush()

        val item = historyOf(invitee, InviteHistoryRole.RECEIVED).single()
        assertEquals(InviteOutcome.GROUP_DELETED, item.outcome)
        // 그룹 행이 사라져도 이름으로 무엇에 대한 초대였는지 답할 수 있어야 한다.
        assertEquals("가족", item.group.name)
        assertTrue(item.groupDeleted)
    }

    @Test
    fun `살아 있는 그룹의 이력은 deleted 가 아니다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val groupId = newGroup(owner)
        inviteService.reject(invite(groupId, owner, "invitee@example.com"), invitee)
        flush()

        assertFalse(historyOf(invitee, InviteHistoryRole.RECEIVED).single().groupDeleted)
    }

    @Test
    fun `정원이 차서 수락이 거부되면 이력이 생기지 않고 초대도 남는다`() {
        val owner = newUser()
        val groupId = newGroup(owner)
        // 소유자 + 4명 = 정원 5명.
        repeat(4) { i ->
            val member = newUser("member$i@example.com")
            inviteService.accept(invite(groupId, owner, "member$i@example.com"), member)
        }
        val latecomer = newUser("late@example.com")
        val inviteId = invite(groupId, owner, "late@example.com")
        flush()

        assertEquals(
            ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED,
            assertThrows<ApiException> { inviteService.accept(inviteId, latecomer) }.errorCode,
        )
        flush()

        // 끝난 것이 아니므로 이력이 아니다. 자리가 나면 같은 초대로 다시 수락할 수 있어야 한다.
        assertTrue(historyOf(latecomer, InviteHistoryRole.RECEIVED).isEmpty())
        assertEquals(1, inviteService.listReceived(latecomer, page()).totalElements.toInt())
    }

    @Test
    fun `이력은 당사자 것만 보이고 관점이 섞이지 않는다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val stranger = newUser("stranger@example.com")
        val groupId = newGroup(owner)
        inviteService.reject(invite(groupId, owner, "invitee@example.com"), invitee)
        flush()

        // 내가 받은 건은 보낸 관점에 섞이지 않고, 그 반대도 마찬가지다.
        assertTrue(historyOf(invitee, InviteHistoryRole.SENT).isEmpty())
        assertTrue(historyOf(owner, InviteHistoryRole.RECEIVED).isEmpty())
        // 당사자가 아닌 사람에게는 어느 관점으로도 보이지 않는다.
        assertTrue(historyOf(stranger, InviteHistoryRole.RECEIVED).isEmpty())
        assertTrue(historyOf(stranger, InviteHistoryRole.SENT).isEmpty())
    }

    @Test
    fun `거절한 상대를 다시 초대할 수 있고 이력은 두 건이 된다`() {
        val owner = newUser()
        val invitee = newUser("invitee@example.com")
        val groupId = newGroup(owner)

        inviteService.reject(invite(groupId, owner, "invitee@example.com"), invitee)
        flush()
        // unique(group_id, invitee_id) 가 비어 있어야 재초대가 가능하다 (명세 §3.2).
        inviteService.reject(invite(groupId, owner, "invitee@example.com"), invitee)
        flush()

        assertEquals(2, historyOf(owner, InviteHistoryRole.SENT).size)
    }

    @Test
    fun `보낸 대기 초대는 그룹을 가로질러 모이고 남의 초대는 섞이지 않는다`() {
        val owner = newUser()
        val otherOwner = newUser()
        newUser("invitee@example.com")
        invite(newGroup(owner), owner, "invitee@example.com")
        invite(newGroup(owner), owner, "invitee@example.com")
        invite(newGroup(otherOwner), otherOwner, "invitee@example.com")
        flush()

        assertEquals(2, inviteService.listSent(owner, page()).totalElements.toInt())
        assertEquals(1, inviteService.listSent(otherOwner, page()).totalElements.toInt())
    }

    private fun historyOf(userId: Long, role: InviteHistoryRole) =
        inviteService.listHistory(userId, role, page()).content

    private fun invite(groupId: Long, ownerId: Long, email: String): Long =
        inviteService.invite(groupId, ownerId, InviteRequest(email)).invite.id

    private fun newGroup(ownerId: Long): Long =
        requireNotNull(groupService.create(ownerId, GroupCreateRequest("가족")).id)

    private fun newUser(email: String = "tester-${System.nanoTime()}@example.com"): Long = requireNotNull(
        userRepository.save(
            User(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = email,
                name = "테스터",
            ),
        ).id,
    )

    private fun page() = PageRequest.of(0, DEFAULT_PAGE_SIZE)

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
