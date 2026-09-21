package com.yong.travel

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.web.DEFAULT_PAGE_SIZE
import com.yong.travel.group.dto.GroupRequest
import com.yong.travel.group.dto.InviteRequest
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import com.yong.travel.record.domain.Category
import com.yong.travel.record.dto.TripRecordCreateRequest
import com.yong.travel.record.service.TripRecordService
import com.yong.travel.trip.domain.Visibility
import com.yong.travel.trip.dto.TripCreateRequest
import com.yong.travel.trip.dto.TripListQuery
import com.yong.travel.trip.dto.TripScope
import com.yong.travel.trip.dto.TripSort
import com.yong.travel.trip.dto.TripUpdateRequest
import com.yong.travel.trip.dto.TripVisibilityUpdateRequest
import com.yong.travel.trip.service.TripService
import jakarta.persistence.EntityManager
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 여행 단위의 공개 범위와 소유자 판정 검증.
 *
 * 공개 범위가 여행 한 곳에만 있으므로 여기서 새면 하위 기록이 전부 함께 샌다.
 * "볼 수 있어야 하는 경우" 보다 "보이면 안 되는 경우" 를 더 촘촘히 확인한다.
 *
 * 1차 캐시 때문에 변경 직후 조회가 DB 를 타지 않으므로 검증 전에 flush/clear 로 영속성 컨텍스트를 비운다.
 */
@SpringBootTest
@Transactional
class TripVisibilityTest {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var validator: Validator
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tripService: TripService
    @Autowired private lateinit var recordService: TripRecordService
    @Autowired private lateinit var groupService: GroupService
    @Autowired private lateinit var inviteService: InviteService

    @Test
    fun `공개 범위를 지정하지 않으면 비공개로 저장된다`() {
        val owner = newUser()
        val other = newUser()
        val tripId = newTrip(owner)
        flush()

        assertEquals(Visibility.PRIVATE, tripService.get(tripId, owner).visibility)
        assertEquals(ErrorCode.TRIP_NOT_FOUND, assertThrows<ApiException> { tripService.get(tripId, other) }.errorCode)
        assertEquals(ErrorCode.TRIP_NOT_FOUND, assertThrows<ApiException> { tripService.get(tripId, null) }.errorCode)
    }

    @Test
    fun `열람자에게는 공개 범위와 공유 그룹을 감추지만 예산은 보여 준다`() {
        val owner = newUser()
        val guest = newUser()
        val groupId = newGroupWith(owner, guest)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId), budget = 1_250_000L)
        flush()

        val asOwner = tripService.get(tripId, owner)
        assertTrue(asOwner.isOwner)
        assertEquals(Visibility.GROUP, asOwner.visibility)
        assertEquals(listOf(groupId), asOwner.sharedGroups?.map { it.id })

        val asGuest = tripService.get(tripId, guest)
        assertFalse(asGuest.isOwner)
        assertNull(asGuest.visibility, "누구에게 공유했는지는 열람자에게 알릴 이유가 없다")
        assertNull(asGuest.sharedGroups)
        // 예산은 공개 범위를 그대로 따르는 값이라 열람자에게도 보인다 (명세 §4.3.1).
        assertEquals(1_250_000L, asGuest.budget)
    }

    @Test
    fun `속하지 않은 그룹에는 공유할 수 없다`() {
        val owner = newUser()
        val stranger = newUser()
        val foreignGroupId = requireNotNull(groupService.create(stranger, GroupRequest("남의 그룹")).id)
        flush()

        // 403 이면 "그런 그룹이 있다" 는 뜻이 되므로 여기서도 404 다.
        val onCreate = assertThrows<ApiException> {
            newTrip(owner, Visibility.GROUP, listOf(foreignGroupId))
        }
        assertEquals(ErrorCode.GROUP_NOT_FOUND, onCreate.errorCode)

        val tripId = newTrip(owner)
        val onChange = assertThrows<ApiException> {
            tripService.changeVisibility(
                tripId,
                owner,
                TripVisibilityUpdateRequest(Visibility.GROUP, listOf(foreignGroupId)),
            )
        }
        assertEquals(ErrorCode.GROUP_NOT_FOUND, onChange.errorCode)
    }

    @Test
    fun `공개 범위가 GROUP 이 아니게 되면 공유 행이 지워진다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        flush()
        assertEquals(1L, count("select count(*) from trip_shares where trip_id = $tripId"))

        tripService.changeVisibility(tripId, owner, TripVisibilityUpdateRequest(Visibility.PRIVATE))
        flush()

        // 남겨 두면 나중에 다시 GROUP 으로 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
        assertEquals(0L, count("select count(*) from trip_shares where trip_id = $tripId"))
        assertThrows<ApiException> { tripService.get(tripId, member) }
    }

    @Test
    fun `공유 그룹 목록은 전체 교체이며 빠진 그룹의 공유는 해제된다`() {
        val owner = newUser()
        val keeper = newUser()
        val dropped = newUser()
        val keepGroupId = newGroupWith(owner, keeper)
        val dropGroupId = newGroupWith(owner, dropped)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(keepGroupId, dropGroupId))
        flush()
        assertEquals(tripId, tripService.get(tripId, dropped).id)

        tripService.changeVisibility(
            tripId,
            owner,
            TripVisibilityUpdateRequest(Visibility.GROUP, listOf(keepGroupId)),
        )
        flush()

        assertEquals(tripId, tripService.get(tripId, keeper).id)
        assertThrows<ApiException> { tripService.get(tripId, dropped) }
    }

    @Test
    fun `여행을 삭제하면 하위 기록도 함께 사라지지만 행은 남는다`() {
        val owner = newUser()
        val tripId = newTrip(owner, Visibility.PUBLIC)
        val recordId = newRecord(tripId, owner)
        flush()

        tripService.delete(tripId, owner)
        flush()

        assertThrows<ApiException> { tripService.get(tripId, owner) }
        assertThrows<ApiException> { recordService.get(recordId, owner) }
        assertEquals(1L, count("select count(*) from trips where id = $tripId"))
        assertEquals(1L, count("select count(*) from trip_records where id = $recordId"))
        // 주인 없는 공유 행이 권한 판정에 끼어들 여지를 남기지 않는다.
        assertEquals(0L, count("select count(*) from trip_shares where trip_id = $tripId"))
    }

    @Test
    fun `소유자가 아니면 수정도 공개 범위 변경도 삭제도 할 수 없다`() {
        val owner = newUser()
        val guest = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(owner, guest)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        flush()

        // 볼 수 있는 사람에게는 403 이다 — 여행의 존재는 이미 알고 있다.
        assertEquals(ErrorCode.FORBIDDEN, assertThrows<ApiException> { tripService.delete(tripId, guest) }.errorCode)
        assertEquals(
            ErrorCode.FORBIDDEN,
            assertThrows<ApiException> {
                tripService.changeVisibility(tripId, guest, TripVisibilityUpdateRequest(Visibility.PUBLIC))
            }.errorCode,
        )
        // 볼 수도 없는 사람에게는 존재부터 숨긴다.
        assertEquals(ErrorCode.TRIP_NOT_FOUND, assertThrows<ApiException> { tripService.delete(tripId, stranger) }.errorCode)
    }

    @Test
    fun `목록 범위는 권한을 넓히지 못한다`() {
        val owner = newUser()
        val member = newUser()
        val stranger = newUser()
        val groupId = newGroupWith(owner, member)

        val privateId = newTrip(owner)
        val sharedId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        val publicId = newTrip(owner, Visibility.PUBLIC)
        flush()

        assertTrue(list(TripScope.MINE, stranger).isEmpty())
        assertEquals(setOf(privateId, sharedId, publicId), list(TripScope.MINE, owner).toSet())

        // SHARED 는 공유받은 타인의 여행만. 내 여행은 MINE 에 이미 있으므로 제외된다.
        assertEquals(listOf(sharedId), list(TripScope.SHARED, member))
        assertTrue(list(TripScope.SHARED, stranger).isEmpty())
        assertTrue(list(TripScope.SHARED, owner).isEmpty())

        assertEquals(listOf(publicId), list(TripScope.PUBLIC, null))
        assertEquals(listOf(publicId), list(TripScope.PUBLIC, stranger))
    }

    @Test
    fun `기록 수는 삭제된 기록을 빼고 세며 기록이 없으면 0 이다`() {
        val owner = newUser()
        val emptyTripId = newTrip(owner, Visibility.PUBLIC)
        val tripId = newTrip(owner, Visibility.PUBLIC)
        val kept = newRecord(tripId, owner)
        val removed = newRecord(tripId, owner)
        flush()

        recordService.delete(removed, owner)
        flush()

        assertEquals(0L, tripService.get(emptyTripId, owner).recordCount)
        assertEquals(1L, tripService.get(tripId, owner).recordCount)
        assertNotNull(kept)

        // 목록도 같은 값을 내려준다 (집계 경로가 갈라지지 않는지 확인).
        val counts = tripService.list(TripListQuery(TripScope.MINE), owner, firstPage())
            .content.associate { it.id to it.recordCount }
        assertEquals(0L, counts[emptyTripId])
        assertEquals(1L, counts[tripId])
    }

    @Test
    fun `시작일순 정렬은 시작일 역순이고 기본은 생성 역순이다`() {
        val owner = newUser()
        val older = newTrip(owner, startDate = LocalDate.of(2026, 1, 1))
        val newer = newTrip(owner, startDate = LocalDate.of(2026, 9, 1))
        flush()

        assertEquals(listOf(newer, older), list(TripScope.MINE, owner, sort = TripSort.START_DATE))
        // RECENT 는 생성 역순이라 나중에 만든 것이 앞이다.
        assertEquals(listOf(newer, older), list(TripScope.MINE, owner, sort = TripSort.RECENT))
    }

    @Test
    fun `이름으로 검색할 수 있고 권한을 넘지 않는다`() {
        val owner = newUser()
        val stranger = newUser()
        newTrip(owner, Visibility.PUBLIC, name = "제주 3박 4일")
        newTrip(owner, Visibility.PUBLIC, name = "부산 여행")
        val hidden = newTrip(owner, name = "제주 비밀 여행")
        flush()

        assertEquals(1, list(TripScope.PUBLIC, stranger, keyword = "제주").size)
        assertTrue(list(TripScope.PUBLIC, stranger, keyword = "제주").none { it == hidden })
        assertEquals(2, list(TripScope.MINE, owner, keyword = "제주").size)
    }

    @Test
    fun `기간이 뒤집힌 요청은 거부된다`() {
        val request = TripCreateRequest(
            name = "거꾸로 여행",
            startDate = LocalDate.of(2026, 9, 8),
            endDate = LocalDate.of(2026, 9, 5),
            headcount = 2,
        )
        assertTrue(validator.validate(request).isNotEmpty(), "endDate 가 startDate 보다 앞설 수 없다")

        val sameDay = TripCreateRequest(
            name = "당일치기",
            startDate = LocalDate.of(2026, 9, 5),
            endDate = LocalDate.of(2026, 9, 5),
            headcount = 1,
        )
        assertTrue(validator.validate(sameDay).isEmpty(), "같은 날이면 당일치기로 허용된다")
    }

    @Test
    fun `인원과 예산은 음수를 받지 않는다`() {
        val badHeadcount = TripCreateRequest(
            name = "여행",
            startDate = LocalDate.of(2026, 9, 5),
            endDate = LocalDate.of(2026, 9, 8),
            headcount = 0,
        )
        assertTrue(validator.validate(badHeadcount).isNotEmpty())

        val badBudget = TripCreateRequest(
            name = "여행",
            startDate = LocalDate.of(2026, 9, 5),
            endDate = LocalDate.of(2026, 9, 8),
            headcount = 1,
            budget = -1L,
        )
        assertTrue(validator.validate(badBudget).isNotEmpty())
    }

    @Test
    fun `정보 수정은 공개 범위를 건드리지 않는다`() {
        val owner = newUser()
        val member = newUser()
        val groupId = newGroupWith(owner, member)
        val tripId = newTrip(owner, Visibility.GROUP, listOf(groupId))
        flush()

        tripService.update(
            tripId,
            owner,
            TripUpdateRequest(
                name = "이름만 바꾼 여행",
                startDate = LocalDate.of(2026, 9, 5),
                endDate = LocalDate.of(2026, 9, 8),
                headcount = 4,
            ),
        )
        flush()

        val updated = tripService.get(tripId, owner)
        assertEquals("이름만 바꾼 여행", updated.name)
        assertEquals(Visibility.GROUP, updated.visibility)
        assertEquals(listOf(groupId), updated.sharedGroups?.map { it.id })
        assertEquals(tripId, tripService.get(tripId, member).id, "공유받은 사람은 그대로 볼 수 있다")
    }

    private fun firstPage() = PageRequest.of(0, DEFAULT_PAGE_SIZE)

    private fun list(
        scope: TripScope,
        userId: Long?,
        keyword: String? = null,
        sort: TripSort = TripSort.RECENT,
    ): List<Long> =
        tripService.list(TripListQuery(scope, keyword, sort), userId, firstPage()).content.map { it.id }

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

    /** 소유자 + 멤버 1명짜리 그룹을 만들고 그룹 id 를 돌려준다. */
    private fun newGroupWith(ownerId: Long, memberId: Long): Long {
        val groupId = requireNotNull(groupService.create(ownerId, GroupRequest("가족")).id)
        val memberEmail = requireNotNull(userRepository.findById(memberId).orElseThrow().email)
        val invite = inviteService.invite(groupId, ownerId, InviteRequest(memberEmail))
        inviteService.accept(invite.invite.id, memberId)
        flush()
        return groupId
    }

    private fun newTrip(
        ownerId: Long,
        visibility: Visibility = Visibility.PRIVATE,
        groupIds: List<Long> = emptyList(),
        name: String = "테스트 여행",
        startDate: LocalDate = LocalDate.of(2026, 9, 5),
        budget: Long? = null,
    ): Long = tripService.create(
        ownerId,
        TripCreateRequest(
            name = name,
            startDate = startDate,
            endDate = startDate.plusDays(3),
            headcount = 2,
            budget = budget,
            visibility = visibility,
            groupIds = groupIds,
        ),
    ).id

    private fun newRecord(tripId: Long, ownerId: Long): Long = recordService.create(
        ownerId,
        TripRecordCreateRequest(
            tripId = tripId,
            name = "테스트 기록",
            category = Category.FOOD,
            address = "서울시 어딘가",
            latitude = 37.5,
            longitude = 127.0,
            rating = 4.5,
        ),
    ).id

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun count(sql: String): Long =
        (entityManager.createNativeQuery(sql).singleResult as Number).toLong()
}
