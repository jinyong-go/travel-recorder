package com.yong.travel.record.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.VisitRecordShare
import com.yong.travel.group.dto.GroupSummaryResponse
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.VisitRecordShareRepository
import com.yong.travel.group.service.GroupService
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.record.domain.VisitRecord
import com.yong.travel.record.domain.Visibility
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.RecordSort
import com.yong.travel.record.dto.VisibilityUpdateRequest
import com.yong.travel.record.dto.VisitRecordCreateRequest
import com.yong.travel.record.dto.VisitRecordResponse
import com.yong.travel.record.dto.VisitRecordSummaryResponse
import com.yong.travel.record.dto.VisitRecordUpdateRequest
import com.yong.travel.record.repository.VisitRecordRepository
import com.yong.travel.record.repository.VisitRecordSpecifications
import com.yong.travel.tag.service.TagService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VisitRecordServiceImpl(
    private val recordRepository: VisitRecordRepository,
    private val userRepository: UserRepository,
    private val tagService: TagService,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
    private val groupService: GroupService,
    private val groupMemberRepository: GroupMemberRepository,
    private val shareRepository: VisitRecordShareRepository,
) : VisitRecordService {

    override fun list(
        query: RecordListQuery,
        userId: Long?,
        pageable: Pageable,
    ): PageResponse<VisitRecordSummaryResponse> {
        val groupIds = groupService.groupIdsOf(userId)
        val spec = VisitRecordSpecifications.withScope(query.scope, userId, groupIds)
            .and(VisitRecordSpecifications.withFilters(query.category, query.tag, query.keyword))

        // 평점은 기록의 컬럼이라 DB 에서 그대로 정렬된다.
        // 거리만 저장된 값이 아니라 요청 좌표에 따라 매번 달라져 페이지 안에서 재정렬한다.
        val sort = when (query.sort) {
            RecordSort.RECENT, RecordSort.DISTANCE -> Sort.by(Sort.Direction.DESC, "createdAt")
            RecordSort.RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }
        val page = recordRepository.findAll(spec, PageRequest.of(pageable.pageNumber, pageable.pageSize, sort))

        val summaries = page.content.map { it.toSummaryResponse(userId, query.lat, query.lng) }
        val sorted = if (query.sort == RecordSort.DISTANCE) {
            summaries.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            summaries
        }

        return PageResponse(sorted, page.number, page.size, page.totalElements, page.totalPages)
    }

    override fun get(recordId: Long, userId: Long?): VisitRecordResponse {
        val record = findRecord(recordId)
        requireViewable(record, userId)
        return record.toResponse(userId)
    }

    @Transactional
    override fun create(authorId: Long, request: VisitRecordCreateRequest): VisitRecordResponse {
        val author = userRepository.findById(authorId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val record = VisitRecord(
            author = author,
            name = request.name,
            category = request.category,
            address = request.address,
            roadAddress = request.roadAddress,
            externalLink = request.externalLink,
            latitude = request.latitude,
            longitude = request.longitude,
            rating = request.rating,
            memo = request.memo,
            visibility = request.visibility,
        )
        record.tags = tagService.findOrCreateAll(request.tags).toMutableSet()
        val saved = recordRepository.save(record)

        applyShares(saved, request.visibility, request.groupIds, authorId)
        return saved.toResponse(authorId)
    }

    @Transactional
    override fun update(
        recordId: Long,
        authorId: Long,
        request: VisitRecordUpdateRequest,
    ): VisitRecordResponse {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.name = request.name
        record.category = request.category
        record.address = request.address
        record.roadAddress = request.roadAddress
        record.externalLink = request.externalLink
        record.latitude = request.latitude
        record.longitude = request.longitude
        record.rating = request.rating
        record.memo = request.memo
        record.visibility = request.visibility
        record.tags = tagService.findOrCreateAll(request.tags).toMutableSet()

        applyShares(record, request.visibility, request.groupIds, authorId)

        // updatedAt 을 채우는 @PreUpdate 는 flush 시점에 돈다.
        return recordRepository.saveAndFlush(record).toResponse(authorId)
    }

    @Transactional
    override fun changeVisibility(
        recordId: Long,
        authorId: Long,
        request: VisibilityUpdateRequest,
    ): VisitRecordResponse {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        record.visibility = request.visibility
        applyShares(record, request.visibility, request.groupIds, authorId)
        return recordRepository.saveAndFlush(record).toResponse(authorId)
    }

    /**
     * soft delete. 사진은 soft delete 대상이 아니라 파일과 메타데이터를 그대로 두는데,
     * 기록이 보이지 않는 동안에는 어차피 조회 경로가 없다.
     * 공유 관계는 물리 삭제한다 — 되살릴 일이 없고, 남아 있으면 권한 판정에 끼어들 수 있다.
     */
    @Transactional
    override fun delete(recordId: Long, authorId: Long) {
        val record = findRecord(recordId)
        requireAuthor(record, authorId)

        shareRepository.deleteByRecordId(recordId)
        record.softDelete()
    }

    /**
     * 공유 그룹 목록을 통째로 갈아 끼운다.
     * GROUP 이 아닌 값으로 바뀌면 기존 공유를 지운다 — 남겨 두면 나중에 다시 GROUP 으로
     * 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
     */
    private fun applyShares(
        record: VisitRecord,
        visibility: Visibility,
        groupIds: List<Long>,
        authorId: Long,
    ) {
        val recordId = requireNotNull(record.id)
        shareRepository.deleteByRecordId(recordId)
        if (visibility != Visibility.GROUP) return

        // 속하지 않은 그룹에는 공유할 수 없다. 그런 그룹 id 는 404 로 막아 존재 여부도 알리지 않는다.
        val groups = groupService.requireAccessibleGroups(groupIds, authorId)
        shareRepository.saveAll(groups.map { VisitRecordShare(record = record, group = it) })
    }

    private fun findRecord(recordId: Long): VisitRecord =
        recordRepository.findById(recordId).orElseThrow { ApiException(ErrorCode.RECORD_NOT_FOUND) }

    /**
     * 볼 수 있는 조건은 셋 중 하나다 — 내가 쓴 기록이거나, 전체 공개이거나,
     * 그룹 공유인데 내가 그 그룹의 멤버이거나.
     */
    private fun requireViewable(record: VisitRecord, userId: Long?) {
        if (canView(record, userId)) return
        // 권한 없음(403)이 아니라 없는 기록(404)으로 답한다. 403 은 "그 기록이 있다"는 뜻이 되기 때문이다.
        throw ApiException(ErrorCode.RECORD_NOT_FOUND)
    }

    private fun canView(record: VisitRecord, userId: Long?): Boolean {
        if (userId != null && record.author.id == userId) return true
        if (record.visibility == Visibility.PUBLIC) return true
        if (record.visibility != Visibility.GROUP || userId == null) return false

        val groupIds = groupService.groupIdsOf(userId)
        return groupIds.isNotEmpty() &&
            shareRepository.existsByRecordIdAndGroupIdIn(requireNotNull(record.id), groupIds)
    }

    private fun requireAuthor(record: VisitRecord, userId: Long) {
        // 볼 수도 없는 기록이면 존재부터 숨긴다. 볼 수 있는데 작성자가 아닌 경우에만 403 이다.
        requireViewable(record, userId)
        if (record.author.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun sharedGroupsOf(recordId: Long, requesterId: Long): List<GroupSummaryResponse> =
        shareRepository.findByRecordId(recordId).map { share ->
            val group: Group = share.group
            val groupId = requireNotNull(group.id)
            GroupSummaryResponse(
                id = groupId,
                name = group.name,
                memberCount = groupMemberRepository.countByGroupId(groupId),
                memberLimit = Group.MEMBER_LIMIT,
                isOwner = group.owner.id == requesterId,
            )
        }

    private fun VisitRecord.toResponse(requesterId: Long?): VisitRecordResponse {
        val recordId = requireNotNull(id)
        val mine = requesterId != null && author.id == requesterId
        return VisitRecordResponse(
            id = recordId,
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            roadAddress = roadAddress,
            externalLink = externalLink,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId)
                .map { PhotoResponse(requireNotNull(it.id), photoStorageService.resolveUrl(it.storageKey)) },
            author = author.toResponse(),
            isAuthor = mine,
            // 공개 범위와 공유 대상은 작성자만 아는 정보다.
            visibility = if (mine) visibility else null,
            sharedGroups = if (mine && visibility == Visibility.GROUP) {
                sharedGroupsOf(recordId, requireNotNull(requesterId))
            } else {
                null
            },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun VisitRecord.toSummaryResponse(
        requesterId: Long?,
        lat: Double?,
        lng: Double?,
    ): VisitRecordSummaryResponse {
        val recordId = requireNotNull(id)
        val mine = requesterId != null && author.id == requesterId
        val photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId)
        return VisitRecordSummaryResponse(
            id = recordId,
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            memo = memo,
            thumbnailUrl = photos.firstOrNull()?.let { photoStorageService.resolveUrl(it.storageKey) },
            photoCount = photos.size.toLong(),
            author = author.toResponse(),
            isAuthor = mine,
            visibility = if (mine) visibility else null,
            distanceKm = if (lat != null && lng != null) {
                haversineKm(lat, lng, latitude, longitude).roundTo2Decimals()
            } else {
                null
            },
            createdAt = createdAt,
        )
    }
}
