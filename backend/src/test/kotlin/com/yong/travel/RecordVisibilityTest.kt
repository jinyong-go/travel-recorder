package com.yong.travel

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.web.DEFAULT_PAGE_SIZE
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.persistence.GroupInviteRepository
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import com.yong.travel.group.persistence.GroupRepository
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.RecordListQuery
import com.yong.travel.record.domain.RecordScope
import com.yong.travel.record.presentation.TripRecordCreateRequest
import com.yong.travel.record.service.TripRecordService
import com.yong.travel.trip.persistence.TripEntity
import com.yong.travel.trip.persistence.TripShareEntity
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.persistence.TripRepository
import com.yong.travel.trip.persistence.TripShareRepository
import jakarta.persistence.EntityManager
import java.time.LocalDate
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
    @Autowired private lateinit var recordService: TripRecordService
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var inviteService: InviteService
    @Autowired private lateinit var inviteRepository: GroupInviteRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var tripRepository: TripRepository
    @Autowired private lateinit var tripShareRepository: TripShareRepository

    @Test
    fun `공개 범위를 지정하지 않은 여행의 기록은 비공개다`() {
        val owner = newUser()
        val other = newUser()
        val recordId = newRecord(newTrip(owner))
        flush()

        assertEquals(recordId, recordService.get(recordId, owner).id)
        assertEquals(ErrorCode.RECORD_NOT_FOUND, assertThrows<ApiException> { recordService.get(recordId, other) }.errorCode)
    }

    @Test
    fun `비공개 여행의 기록은 남에게도 비로그인에게도 404 다`() {
        val owner = newUser()
        val other = newUser()
        val recordId = newRecord(newTrip(owner))
        flush()

        // 403 이 아니라 404 여야 한다. 403 은 "그 기록이 존재한다" 는 사실을 알려주는 셈이다.
        assertEquals(ErrorCode.RECORD_NOT_FOUND, assertThrows<ApiException> { recordService.get(recordId, other) }.errorCode)
        assertEquals(ErrorCode.RECORD_NOT_FOUND, assertThrows<ApiException> { recordService.get(recordId, null) }.errorCode)
    }

    @Test
    fun `전체 공개 여행의 기록은 비로그인도 볼 수 있고 소속 여행을 가리킨다`() {
        val owner = newUser()
        val other = newUser()
        val tripId = newTrip(owner, Visibility.PUBLIC)
        val recordId = newRecord(tripId)
        flush()

        val asOwner = recordService.get(recordId, owner)
        assertTrue(asOwner.isAuthoredBy(owner))
        assertEquals(tripId, asOwner.trip.id)

        val asGuest = recordService.get(recordId, null)
        assertFalse(asGuest.isAuthoredBy(null))
        // 작성자는 기록이 아니라 여행 소유자에서 온다.
        assertEquals(owner, asGuest.author.id)
        assertEquals(tripId, recordService.get(recordId, other).trip.id)
    }

    @Test
    fun `그룹 공유 여행의 기록은 멤버만 볼 수 있고 비멤버에게는 404 다`() {
        val owner = newUser()
        val member = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(owner, member)

        val recordId = newRecord(newTrip(owner, Visibility.GROUP, listOf(groupId)))
        flush()

        assertEquals(recordId, recordService.get(recordId, member).id)
        assertThrows<ApiException> { recordService.get(recordId, stranger) }
        assertThrows<ApiException> { recordService.get(recordId, null) }
    }

    @Test
    fun `여행의 공개 범위를 좁히면 하위 기록이 곧바로 보이지 않는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        val recordId = newRecord(tripId)
        flush()
        assertEquals(recordId, recordService.get(recordId, member).id)

        // 기록은 손대지 않는다. 범위는 여행 한 곳에만 있어서 여행만 바꾸면 하위가 전부 따라온다.
        changeTripVisibility(tripId, Visibility.PRIVATE)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, member) }
        assertTrue(list(RecordScope.SHARED, member).isEmpty())
    }

    @Test
    fun `그룹에서 탈퇴하면 그 그룹으로 공유된 여행의 기록이 보이지 않는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val recordId = newRecord(newTrip(owner, Visibility.GROUP, listOf(groupId)))
        flush()

        groupService.leave(groupId, member)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, member) }
    }

    @Test
    fun `그룹을 삭제해도 여행과 기록은 남고 비공개처럼 동작한다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        val recordId = newRecord(tripId)
        flush()

        groupService.delete(groupId, owner)
        flush()

        assertEquals(recordId, recordService.get(recordId, owner).id, "기록 자체는 삭제되지 않는다")
        assertThrows<ApiException> { recordService.get(recordId, member) }
        // 주인 없는 공유 행이 남아 권한 판정에 끼어들면 안 된다.
        assertEquals(0L, count("select count(*) from trip_shares where trip_id = $tripId"))
    }

    @Test
    fun `남의 여행에는 기록을 넣을 수도 옮길 수도 없다`() {
        val owner = newUser()
        val stranger = newUser()
        val foreignTripId = newTrip(owner, Visibility.PUBLIC)
        val myTripId = newTrip(stranger)
        val myRecordId = newRecord(myTripId, authorId = stranger)
        flush()

        // 볼 수 있는 공개 여행이라도 남의 것이면 404 다. 403 이면 그 여행의 존재가 드러난다.
        assertEquals(
            ErrorCode.TRIP_NOT_FOUND,
            assertThrows<ApiException> { newRecord(foreignTripId, authorId = stranger) }.errorCode,
        )
        assertEquals(
            ErrorCode.TRIP_NOT_FOUND,
            assertThrows<ApiException> {
                recordService.changeTrip(myRecordId, stranger, foreignTripId)
            }.errorCode,
        )
    }

    @Test
    fun `기록을 옮기면 공개 범위는 새 여행의 것을 따른다`() {
        val owner = newUser()
        val stranger = newUser()
        val privateTripId = newTrip(owner)
        val publicTripId = newTrip(owner, Visibility.PUBLIC)
        val recordId = newRecord(privateTripId)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, stranger) }

        recordService.changeTrip(recordId, owner, publicTripId)
        flush()

        assertEquals(publicTripId, recordService.get(recordId, stranger).trip.id)
    }

    @Test
    fun `목록 범위는 권한을 넓히지 못한다`() {
        val owner = newUser()
        val member = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(owner, member)

        val privateId = newRecord(newTrip(owner), name = "비공개")
        val sharedId = newRecord(newTrip(owner, Visibility.GROUP, listOf(groupId)), name = "그룹공유")
        val publicId = newRecord(newTrip(owner, Visibility.PUBLIC), name = "전체공개")
        flush()

        // 남이 MINE 으로 조회해도 남의 기록이 딸려 나오지 않는다.
        assertTrue(list(RecordScope.MINE, stranger).isEmpty())
        assertEquals(setOf(privateId, sharedId, publicId), list(RecordScope.MINE, owner).toSet())

        // SHARED 는 공유받은 타인의 기록만. 내 기록은 MINE 에 이미 있으므로 제외된다.
        assertEquals(listOf(sharedId), list(RecordScope.SHARED, member))
        assertTrue(list(RecordScope.SHARED, stranger).isEmpty())
        assertTrue(list(RecordScope.SHARED, owner).isEmpty())

        // PUBLIC 은 비로그인도 볼 수 있고, 공개된 것만 나온다.
        assertEquals(listOf(publicId), list(RecordScope.PUBLIC, null))
        assertEquals(listOf(publicId), list(RecordScope.PUBLIC, stranger))
    }

    @Test
    fun `tripId 필터는 권한을 넓히지 못한다`() {
        val owner = newUser()
        val stranger = newUser()
        val privateTripId = newTrip(owner)
        val recordId = newRecord(privateTripId)
        flush()

        // 볼 수 없는 여행의 id 를 찍어도 오류가 아니라 빈 목록이다 (명세 §4.4.1).
        assertTrue(list(RecordScope.PUBLIC, stranger, privateTripId).isEmpty())
        assertTrue(list(RecordScope.MINE, stranger, privateTripId).isEmpty())
        assertEquals(listOf(recordId), list(RecordScope.MINE, owner, privateTripId))
    }

    @Test
    fun `기록을 삭제하면 목록과 상세에서 사라지지만 행은 남는다`() {
        val owner = newUser()
        val recordId = newRecord(newTrip(owner, Visibility.PUBLIC))
        flush()

        recordService.delete(recordId, owner)
        flush()

        assertThrows<ApiException> { recordService.get(recordId, owner) }
        assertTrue(list(RecordScope.PUBLIC, null).none { it == recordId })
        assertEquals(1L, count("select count(*) from trip_records where id = $recordId"))
    }

    @Test
    fun `같은 장소를 다시 기록해도 중복으로 막지 않는다`() {
        val owner = newUser()
        val tripId = newTrip(owner)
        val first = newRecord(tripId, name = "성산일출봉")
        val second = newRecord(tripId, name = "성산일출봉")
        flush()

        assertTrue(first != second)
        assertEquals(2, list(RecordScope.MINE, owner).size)
    }

    @Test
    fun `이메일로 보낸 초대는 받은 사람 목록에 쌓이고 수락하면 멤버가 된다`() {
        val owner = newUser()
        val invitee = newUser(email = "friend@example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)

        inviteService.invite(groupId, owner, "friend@example.com")
        flush()

        val received = inviteService.listReceived(invitee, firstPage()).content
        assertEquals(1, received.size)
        assertEquals(groupId, received.single().group.id)

        inviteService.accept(received.single().id, invitee)
        flush()

        assertEquals(2, groupService.get(groupId, owner).members.size)
        // 수락한 초대는 남지 않는다. 상태 컬럼도 이력도 두지 않기 때문이다.
        assertTrue(inviteService.listReceived(invitee, firstPage()).content.isEmpty())
    }

    @Test
    fun `이메일은 대소문자를 구분하지 않고 응답에 담기지 않는다`() {
        val owner = newUser()
        newUser(email = "Friend@Example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)

        val result = inviteService.invite(groupId, owner, "friend@EXAMPLE.com")
        flush()

        // 소유자가 직접 입력한 값이라도 되돌려주지 않는다. 상대는 이름·프로필 사진으로만 식별한다.
        assertEquals("테스터", result.invite.invitee.name)
    }

    @Test
    fun `대기 중인 초대가 있는 상대를 다시 초대해도 초대가 늘지 않는다`() {
        val owner = newUser()
        newUser(email = "friend@example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)

        val first = inviteService.invite(groupId, owner, "friend@example.com")
        flush()
        val second = inviteService.invite(groupId, owner, "friend@example.com")
        flush()

        assertTrue(first.created)
        assertFalse(second.created, "중복 클릭이 실패처럼 보여서는 안 된다")
        assertEquals(first.invite.id, second.invite.id)
        assertEquals(1, inviteService.listPending(groupId, owner, firstPage()).totalElements)
    }

    @Test
    fun `가입하지 않은 이메일과 이미 멤버인 상대는 초대할 수 없다`() {
        val owner = newUser(email = "owner@example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        flush()

        assertEquals(
            ErrorCode.USER_NOT_FOUND,
            assertThrows<ApiException> { inviteService.invite(groupId, owner, "nobody@example.com") }.errorCode,
        )
        // 소유자도 멤버 행을 가지므로 자기 자신을 초대하면 여기에 걸린다.
        assertEquals(
            ErrorCode.ALREADY_MEMBER,
            assertThrows<ApiException> { inviteService.invite(groupId, owner, "owner@example.com") }.errorCode,
        )
    }

    @Test
    fun `당사자가 아닌 초대는 수락도 거절도 철회도 404 다`() {
        val owner = newUser()
        newUser(email = "friend@example.com")
        val stranger = newUser()
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        val inviteId = inviteService.invite(groupId, owner, "friend@example.com").invite.id
        flush()

        // 403 이면 "그런 초대가 있다" 는 뜻이 되므로 없는 id 와 같은 404 여야 한다.
        assertEquals(ErrorCode.INVITE_NOT_FOUND, assertThrows<ApiException> { inviteService.accept(inviteId, stranger) }.errorCode)
        assertEquals(ErrorCode.INVITE_NOT_FOUND, assertThrows<ApiException> { inviteService.reject(inviteId, stranger) }.errorCode)
        assertEquals(ErrorCode.INVITE_NOT_FOUND, assertThrows<ApiException> { inviteService.revoke(groupId, inviteId, stranger) }.errorCode)
        assertTrue(inviteService.listReceived(stranger, firstPage()).content.isEmpty())
    }

    @Test
    fun `거절한 초대는 사라지고 흔적을 남기지 않아 다시 초대할 수 있다`() {
        val owner = newUser()
        val invitee = newUser(email = "friend@example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        val inviteId = inviteService.invite(groupId, owner, "friend@example.com").invite.id
        flush()

        inviteService.reject(inviteId, invitee)
        flush()

        assertTrue(inviteService.listPending(groupId, owner, firstPage()).content.isEmpty())
        assertTrue(inviteService.invite(groupId, owner, "friend@example.com").created)
    }

    @Test
    fun `정원이 찬 뒤의 수락은 거부되고 초대는 남는다`() {
        val owner = newUser()
        val latecomer = newUser(email = "late@example.com")
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)

        // 대기 중인 초대는 정원을 차지하지 않으므로 정원을 넘겨 보낼 수 있다.
        val inviteId = inviteService.invite(groupId, owner, "late@example.com").invite.id
        // 소유자를 포함해 5명이 정원이므로 4명까지만 더 들어올 수 있다.
        repeat(4) { i ->
            val email = "member$i@example.com"
            val member = newUser(email = email)
            inviteService.accept(inviteService.invite(groupId, owner, email).invite.id, member)
        }
        flush()

        val failure = assertThrows<ApiException> { inviteService.accept(inviteId, latecomer) }
        assertEquals(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED, failure.errorCode)
        // 자리가 나면 같은 초대로 다시 수락할 수 있어야 하므로 행을 지우지 않는다.
        assertTrue(inviteRepository.findById(inviteId).isPresent)
    }

    @Test
    fun `소유자는 탈퇴할 수 없고 제외 대상도 될 수 없다`() {
        val owner = newUser()
        val groupId = requireNotNull(groupService.create(owner, "가족", null).id)
        flush()

        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { groupService.leave(groupId, owner) }.errorCode)
        assertEquals(
            ErrorCode.VALIDATION_ERROR,
            assertThrows<ApiException> { groupService.removeMember(groupId, owner, owner) }.errorCode,
        )
    }

    /** 목록 검증은 첫 페이지만 본다. 페이지 크기는 서버가 정하므로(명세 §4.1) 테스트도 그 값을 그대로 쓴다. */
    private fun firstPage() = PageRequest.of(0, DEFAULT_PAGE_SIZE)

    private fun list(scope: RecordScope, userId: Long?, tripId: Long? = null): List<Long> =
        recordService.list(RecordListQuery(scope, tripId), userId, PageRequest.of(0, 10)).content.map { it.id }

    /** 이메일은 계정마다 고유해야 한다 (users.email 유니크). 초대 대상 조회에 쓰이는 값이라 필요하면 직접 지정한다. */
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
        val invite = inviteService.invite(groupId, ownerId, memberEmail)
        inviteService.accept(invite.invite.id, memberId)
        flush()
        return groupId
    }

    /**
     * 여행을 만들고 id 를 돌려준다.
     *
     * `/api/trips` 가 아직 없어 리포지토리로 직접 만든다. 공유 행 정리 같은 쓰기 규칙은
     * TripService 가 생기면 그쪽 테스트에서 검증한다.
     */
    private fun newTrip(
        ownerId: Long,
        visibility: Visibility = Visibility.PRIVATE,
        groupIds: List<Long> = emptyList(),
    ): Long {
        val trip = tripRepository.save(
            TripEntity(
                owner = userRepository.findById(ownerId).orElseThrow(),
                name = "테스트 여행",
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 8),
                headcount = 2,
                visibility = visibility,
            ),
        )
        groupIds.forEach { groupId ->
            tripShareRepository.save(TripShareEntity(trip = trip, group = groupRepository.findById(groupId).orElseThrow()))
        }
        return requireNotNull(trip.id)
    }

    private fun changeTripVisibility(tripId: Long, visibility: Visibility) {
        val trip = tripRepository.findById(tripId).orElseThrow()
        trip.visibility = visibility
        tripRepository.saveAndFlush(trip)
    }

    /** 기록은 여행 없이 존재할 수 없다. 작성자를 따로 주지 않으면 여행 소유자로 만든다. */
    private fun newRecord(
        tripId: Long,
        name: String = "테스트 기록",
        authorId: Long = requireNotNull(tripRepository.findById(tripId).orElseThrow().owner.id),
    ): Long = recordService.create(
        authorId,
        TripRecordCreateRequest(
            tripId = tripId,
            name = name,
            category = Category.FOOD,
            address = "서울시 어딘가",
            latitude = 37.5,
            longitude = 127.0,
            rating = 4.5,
        ).toCommand(),
    ).id

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun count(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()
}
