package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.service.GroupInviteService
import com.yong.travel.group.service.GroupService
import jakarta.persistence.EntityManager
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 그룹 인가 검증. 그룹은 존재를 숨기지 않아 비멤버·비소유자는 403, 없는 그룹만 404 다.
 *
 * SecurityConfig 가 permitAll 이라 컨트롤러의 requireLogin 이 유일한 인증 차단이다. 빠뜨리면 그대로
 * 열리므로 그룹 엔드포인트마다 비로그인 401 을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupAuthorizationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var groupInviteService: GroupInviteService

    @Test
    fun `비멤버의 그룹 조회는 403 이고 없는 그룹은 404 다`() {
        val owner = newUser()
        val stranger = newUser()
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        flush()

        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.get(groupId, stranger) }.errorCode)
        assertEquals(ErrorCode.GROUP_NOT_FOUND, assertThrows<ApiException> { groupService.get(Long.MAX_VALUE, stranger) }.errorCode)
    }

    @Test
    fun `멤버여도 소유자가 아니면 수정·삭제·멤버 제외·대기 초대 조회가 403 이다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)

        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.update(groupId, member, "바꿈", null) }.errorCode)
        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.delete(groupId, member) }.errorCode)
        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.removeMember(groupId, owner, member) }.errorCode)
        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupInviteService.listPending(groupId, member, 0) }.errorCode)
    }

    @Test
    fun `소유자와 비멤버는 그룹을 나갈 수 없고 403 이다`() {
        val owner = newUser()
        val stranger = newUser()
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        flush()

        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.leave(groupId, owner) }.errorCode)
        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.leave(groupId, stranger) }.errorCode)
    }

    @Test
    fun `그룹 엔드포인트는 비로그인이면 모두 401 UNAUTHENTICATED 다`() {
        val requests = listOf(
            get("/api/groups"),
            post("/api/groups").contentType(MediaType.APPLICATION_JSON).content("""{"name":"가족"}"""),
            get("/api/groups/1"),
            put("/api/groups/1").contentType(MediaType.APPLICATION_JSON).content("""{"name":"가족"}"""),
            delete("/api/groups/1"),
            delete("/api/groups/1/members/me"),
            delete("/api/groups/1/members/2"),
            get("/api/groups/1/invites"),
            post("/api/groups/1/invites").contentType(MediaType.APPLICATION_JSON).content("""{"email":"a@example.com"}"""),
            delete("/api/groups/1/invites/1"),
        )
        requests.forEach { request ->
            mockMvc.perform(request.with(csrf()))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }

    /** 이메일은 계정마다 고유해야 한다 (users.email 유니크). */
    private fun newUser(email: String = "tester-${System.nanoTime()}@example.com"): Long = requireNotNull(
        userRepository.save(
            UserEntity(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = email,
                name = "테스터",
            ),
        ).id,
    )

    /** 소유자 + 멤버 1명짜리 그룹을 만들고 그룹 id 를 돌려준다. */
    private fun newGroupWith(ownerId: Long, memberId: Long): Long {
        val groupId = requireNotNull(groupService.create(ownerId, "가족", null).id)
        val memberEmail = requireNotNull(userRepository.findById(memberId).orElseThrow().email)
        val invite = groupInviteService.invite(groupId, ownerId, memberEmail)
        groupInviteService.accept(invite.invite.id, memberId)
        flush()
        return groupId
    }

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
