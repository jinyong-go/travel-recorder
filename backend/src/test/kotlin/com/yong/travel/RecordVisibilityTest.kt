package com.yong.travel

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.dto.GroupRequest
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.Visibility
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.RecordScope
import com.yong.travel.record.dto.VisibilityUpdateRequest
import com.yong.travel.record.dto.VisitRecordCreateRequest
import com.yong.travel.record.service.VisitRecordService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 공개 범위 판정 검증. 이 서비스에서 실수가 가장 치명적인 지점이라
 * "볼 수 있어야 하는 경우" 보다 "보이면 안 되는 경우" 를 더 촘촘히 확인한다.
 *
 * 1차 캐시 때문에 변경 직후 조회가 DB 를 타지 않으므로 검증 전에 flush/clear 로 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class RecordVisibilityTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var recordService: VisitRecordService
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var inviteService: InviteService

    @Test
    fun `공개 범위를 지정하지 않으면 비공개로 저장된다`() {
        val author = newUser()
        val recordId = newRecord(author)
        flush()

        assertEquals(Visibility.PRIVATE, recordService.get(recordId, author).visibility)
    }

    @Test
    fun `비공개 기록은 남에게도 비로그인에게도 404 다`() {
        val author = newUser()
        val other = newUser()
        val recordId = newRecord(author)
        flush()

        // 403 이 아니라 404 여야 한다. 403 은 "그 기록이 존재한다" 는 사실을 알려주는 셈이다.
        assertEquals(ErrorCode.RECORD_NOT_FOUND, assertThrows<ApiException> { recordService.get(recordId, other) }.errorCode)
        assertEquals(ErrorCode.RECORD_NOT_FOUND, assertThrows<ApiException> { recordService.get(recordId, null) }.errorCode)
    }

    @Test
    fun `전체 공개 기록은 비로그인도 볼 수 있지만 공개 범위는 작성자에게만 보인다`() {
        val author = newUser()
        val other = newUser()
        val recordId = newRecord(author, visibility = Visibility.PUBLIC)
        flush()

        val asAuthor = recordService.get(recordId, author)
        assertTrue(asAuthor.isAuthor)
        assertEquals(Visibility.PUBLIC, asAuthor.visibility)

        val asGuest = recordService.get(recordId, null)
        assertFalse(asGuest.isAuthor)
        assertNull(asGuest.visibility, "열람자에게 공개 범위를 알릴 이유가 없다")
        assertNull(recordService.get(recordId, other).sharedGroups)
    }

    @Test
    fun `그룹 공유 기록은 멤버만 볼 수 있고 비멤버에게는 404 다`() {
        val author = newUser()
        val member = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(author, member)

        val recordId = newRecord(author, visibility = Visibility.GROUP, groupIds = listOf(groupId))
        flush()

        assertEquals(recordId, recordService.get(recordId, member).id)
        assertThrows<ApiException> { recordService.get(recordId, stranger) }
        assertThrows<ApiException> { recordService.get(recordId, null) }
    }

    @Test
    fun `공개 범위를 좁히면 직전까지 보이던 사용자도 곧바로 볼 수 없다`() {
        val author = newUser()
        val member = newUser()
        val groupId = newGroupWith(author, member)
        val recordId = newRecord(author, visibility = Visibility.GROUP, groupIds = listOf(groupId))
        flush()
        assertEquals(recordId, recordService.get(recordId, member).id)

        recordService.changeVisibility(recordId, author, VisibilityUpdateRequest(Visibility.PRIVATE))
        flush()

        assertThrows<ApiException> { recordService.get(recordId, member) }
        // GROUP 이 아니게 되면 공유 행도 지워야 나중에 다시 GROUP 으로 되돌렸을 때 예전 공유가 되살아나지 않는다.
        assertEquals(0L, count("select count(*) from visit_record_share where record_id = $recordId"))
    }

    @Test
    fun `그룹에서 탈퇴하면 그 그룹으로 공유된 기록이 보이지 않는다`() {
        val author = newUser()
        val member = newUser()
        val groupId = newGroupWith(author, member)
        val recordId = newRecord(author, visibility = Visibility.GROUP, groupIds = listOf(groupId))
        flush()

        groupService.leave(groupId, member)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, member) }
    }

    @Test
    fun `그룹을 삭제해도 기록은 남고 비공개처럼 동작한다`() {
        val author = newUser()
        val member = newUser()
        val groupId = newGroupWith(author, member)
        val recordId = newRecord(author, visibility = Visibility.GROUP, groupIds = listOf(groupId))
        flush()

        groupService.delete(groupId, author)
        flush()

        assertEquals(recordId, recordService.get(recordId, author).id, "기록 자체는 삭제되지 않는다")
        assertThrows<ApiException> { recordService.get(recordId, member) }
    }

    @Test
    fun `속하지 않은 그룹에는 공유할 수 없다`() {
        val author = newUser()
        val stranger = newUser()
        val foreignGroupId = requireNotNull(groupService.create(stranger, GroupRequest("남의 그룹")).id)
        flush()

        val failure = assertThrows<ApiException> {
            newRecord(author, visibility = Visibility.GROUP, groupIds = listOf(foreignGroupId))
        }
        // 403 이면 "그런 그룹이 있다" 는 뜻이 되므로 여기서도 404 다.
        assertEquals(ErrorCode.GROUP_NOT_FOUND, failure.errorCode)
    }

    @Test
    fun `목록 범위는 권한을 넓히지 못한다`() {
        val author = newUser()
        val member = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(author, member)

        val privateId = newRecord(author, name = "비공개")
        val groupId2 = newRecord(author, name = "그룹공유", visibility = Visibility.GROUP, groupIds = listOf(groupId))
        val publicId = newRecord(author, name = "전체공개", visibility = Visibility.PUBLIC)
        flush()

        // 남이 MINE 으로 조회해도 남의 기록이 딸려 나오지 않는다.
        assertTrue(list(RecordScope.MINE, stranger).isEmpty())
        assertEquals(setOf(privateId, groupId2, publicId), list(RecordScope.MINE, author).toSet())

        // SHARED 는 공유받은 타인의 기록만. 내 기록은 MINE 에 이미 있으므로 제외된다.
        assertEquals(listOf(groupId2), list(RecordScope.SHARED, member))
        assertTrue(list(RecordScope.SHARED, stranger).isEmpty())
        assertTrue(list(RecordScope.SHARED, author).isEmpty())

        // PUBLIC 은 비로그인도 볼 수 있고, 공개된 것만 나온다.
        assertEquals(listOf(publicId), list(RecordScope.PUBLIC, null))
        assertEquals(listOf(publicId), list(RecordScope.PUBLIC, stranger))
    }

    @Test
    fun `기록을 삭제하면 목록과 상세에서 사라지지만 행은 남는다`() {
        val author = newUser()
        val recordId = newRecord(author, visibility = Visibility.PUBLIC)
        flush()

        recordService.delete(recordId, author)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, author) }
        assertTrue(list(RecordScope.PUBLIC, null).none { it == recordId })
        assertEquals(1L, count("select count(*) from visit_records where id = $recordId"))
    }

    @Test
    fun `같은 장소를 다시 기록해도 중복으로 막지 않는다`() {
        val author = newUser()
        val first = newRecord(author, name = "성산일출봉")
        val second = newRecord(author, name = "성산일출봉")
        flush()

        assertTrue(first != second)
        assertEquals(2, list(RecordScope.MINE, author).size)
    }

    @Test
    fun `초대 링크를 재발급하면 이전 링크는 무효가 된다`() {
        val owner = newUser()
        val invitee = newUser()
        val groupId = requireNotNull(groupService.create(owner, GroupRequest("가족")).id)

        val first = inviteService.issue(groupId, owner)
        val second = inviteService.issue(groupId, owner)
        flush()

        assertTrue(first.token != second.token)
        assertEquals(ErrorCode.INVITE_NOT_FOUND, assertThrows<ApiException> { inviteService.preview(first.token) }.errorCode)
        assertEquals(groupId, inviteService.accept(second.token, invitee))
    }

    @Test
    fun `이미 멤버인 사용자가 초대를 다시 수락해도 오류가 아니다`() {
        val owner = newUser()
        val invitee = newUser()
        val groupId = requireNotNull(groupService.create(owner, GroupRequest("가족")).id)
        val invite = inviteService.issue(groupId, owner)

        inviteService.accept(invite.token, invitee)
        flush()
        inviteService.accept(invite.token, invitee)
        flush()

        assertEquals(2, groupService.get(groupId, owner).members.size)
    }

    @Test
    fun `그룹 정원을 넘는 초대 수락은 거부된다`() {
        val owner = newUser()
        val groupId = requireNotNull(groupService.create(owner, GroupRequest("가족")).id)
        val invite = inviteService.issue(groupId, owner)

        // 소유자를 포함해 5명이 정원이므로 4명까지만 더 들어올 수 있다.
        repeat(4) { inviteService.accept(invite.token, newUser()) }
        flush()

        val failure = assertThrows<ApiException> { inviteService.accept(invite.token, newUser()) }
        assertEquals(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED, failure.errorCode)
    }

    @Test
    fun `소유자는 탈퇴할 수 없고 제외 대상도 될 수 없다`() {
        val owner = newUser()
        val groupId = requireNotNull(groupService.create(owner, GroupRequest("가족")).id)
        flush()

        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.leave(groupId, owner) }.errorCode)
        assertEquals(
            ErrorCode.VALIDATION_ERROR,
            assertThrows<ApiException> { groupService.removeMember(groupId, owner, owner) }.errorCode,
        )
    }

    private fun list(scope: RecordScope, userId: Long?): List<Long> =
        recordService.list(RecordListQuery(scope), userId, PageRequest.of(0, 10)).content.map { it.id }

    private fun newUser(): Long = requireNotNull(
        userRepository.save(
            User(
                provider = "naver",
                providerId = "provider-${System.nanoTime()}",
                email = "tester@example.com",
                name = "테스터",
            ),
        ).id,
    )

    /** 소유자 + 멤버 1명짜리 그룹을 만들고 그룹 id 를 돌려준다. */
    private fun newGroupWith(ownerId: Long, memberId: Long): Long {
        val groupId = requireNotNull(groupService.create(ownerId, GroupRequest("가족")).id)
        val invite = inviteService.issue(groupId, ownerId)
        inviteService.accept(invite.token, memberId)
        flush()
        return groupId
    }

    private fun newRecord(
        authorId: Long,
        name: String = "테스트 기록",
        visibility: Visibility = Visibility.PRIVATE,
        groupIds: List<Long> = emptyList(),
    ): Long = recordService.create(
        authorId,
        VisitRecordCreateRequest(
            name = name,
            category = Category.FOOD,
            address = "서울시 어딘가",
            latitude = 37.5,
            longitude = 127.0,
            rating = 4.5,
            visibility = visibility,
            groupIds = groupIds,
        ),
    ).id

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun count(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()
}
