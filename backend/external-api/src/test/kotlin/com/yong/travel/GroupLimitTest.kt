package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.LoginUserDetails
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.InviteHistoryRole
import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.service.GroupInviteService
import com.yong.travel.group.service.GroupService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 사용자당 그룹 수 상한과 그룹 목록 페이지네이션 검증.
 *
 * 소유 상한은 생성 시점, 참여 상한은 수락 시점에 판정한다. 두 상한이 서로를 막지 않는지와, 참여 상한으로
 * 거부된 수락이 정원 초과처럼 초대를 남기는지를 경계값으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupLimitTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var groupInviteService: GroupInviteService

    @Test
    fun `소유 그룹은 50개까지 만들 수 있고 51번째는 409 GROUP_LIMIT_EXCEEDED 다`() {
        val owner = newUser()
        repeat(GroupEntity.OWNED_GROUP_LIMIT) { groupService.create(owner, "그룹$it", null) }

        val error = assertThrows<ApiException> { groupService.create(owner, "넘침", null) }

        assertEquals(ErrorCode.GROUP_LIMIT_EXCEEDED, error.errorCode)
    }

    @Test
    fun `참여 그룹은 50개까지 수락할 수 있고 넘치면 초대가 남고 이력도 생기지 않는다`() {
        val user = newUser()
        repeat(GroupEntity.JOINED_GROUP_LIMIT) { joinNewGroup(user, "그룹$it") }
        val historyBefore = groupInviteService.listHistory(user, InviteHistoryRole.RECEIVED, 0).totalElements

        val inviteId = invite(user, "넘침")
        val error = assertThrows<ApiException> { groupInviteService.accept(inviteId, user) }

        assertEquals(ErrorCode.GROUP_LIMIT_EXCEEDED, error.errorCode)
        assertEquals(listOf(inviteId), groupInviteService.listReceived(user, 0).content.map { it.id })
        assertEquals(historyBefore, groupInviteService.listHistory(user, InviteHistoryRole.RECEIVED, 0).totalElements)
    }

    @Test
    fun `참여가 49개면 50번째 수락은 된다`() {
        val user = newUser()
        repeat(GroupEntity.JOINED_GROUP_LIMIT - 1) { joinNewGroup(user, "그룹$it") }

        joinNewGroup(user, "마지막")

        assertEquals(
            GroupEntity.JOINED_GROUP_LIMIT.toLong(),
            groupService.list(user, 0).totalElements,
        )
    }

    @Test
    fun `소유 그룹은 참여 상한에 세지 않는다`() {
        val user = newUser()
        repeat(GroupEntity.OWNED_GROUP_LIMIT) { groupService.create(user, "내그룹$it", null) }

        joinNewGroup(user, "남의그룹")

        assertEquals(
            (GroupEntity.OWNED_GROUP_LIMIT + 1).toLong(),
            groupService.list(user, 0).totalElements,
        )
    }

    @Test
    fun `그룹 목록은 이름순 10건 단위 페이지다`() {
        val owner = newUser()
        repeat(12) { groupService.create(owner, "그룹%02d".format(it), null) }
        flush()

        mockMvc.perform(get("/api/groups?page=1").with(loggedInAs(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(12))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("그룹10"))
            .andExpect(jsonPath("$.content[0].isOwner").value(true))
    }

    /** 새 사용자가 만든 그룹에 user 가 초대받아 수락한다. */
    private fun joinNewGroup(userId: Long, name: String) {
        groupInviteService.accept(invite(userId, name), userId)
    }

    /**
     * 새 사용자가 그룹을 만들어 user 에게 초대를 보내고 초대 id 를 돌려준다. 그룹마다 소유자를 따로 두어
     * 소유 상한에 걸리지 않게 한다.
     */
    private fun invite(userId: Long, name: String): Long {
        val hostId = newUser()
        val groupId = requireNotNull(groupService.create(hostId, name, null).id)
        val email = requireNotNull(userRepository.findById(userId).orElseThrow().email)
        return groupInviteService.invite(groupId, hostId, email).invite.id
    }

    /** 컨트롤러가 principal 을 `LoginUser` 로 받으므로 임시 로그인과 같은 타입을 세션 대신 직접 넣는다. */
    private fun loggedInAs(userId: Long) = authentication(
        UsernamePasswordAuthenticationToken(LoginUserDetails("tester", "", userId), null, emptyList()),
    )

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    /** 이메일은 계정마다 고유해야 한다 (users.email 유니크). */
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
}
