package com.yong.travel.place.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.util.haversineKm
import com.yong.travel.common.util.roundTo2Decimals
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.photo.repository.PhotoRepository
import com.yong.travel.photo.storage.PhotoStorageService
import com.yong.travel.place.domain.Place
import com.yong.travel.place.dto.PlaceCreateRequest
import com.yong.travel.place.dto.PlaceListQuery
import com.yong.travel.place.dto.PlaceResponse
import com.yong.travel.place.dto.PlaceSort
import com.yong.travel.place.dto.PlaceSummaryResponse
import com.yong.travel.place.dto.PlaceUpdateRequest
import com.yong.travel.place.dto.RatingSummary
import com.yong.travel.place.repository.PlaceRepository
import com.yong.travel.place.repository.PlaceSpecifications
import com.yong.travel.rating.repository.RatingRepository
import com.yong.travel.tag.service.TagService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PlaceServiceImpl(
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val tagService: TagService,
    private val ratingRepository: RatingRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorageService: PhotoStorageService,
) : PlaceService {

    override fun list(query: PlaceListQuery, pageable: Pageable): PageResponse<PlaceSummaryResponse> {
        val spec = PlaceSpecifications.withFilters(query.category, query.tag, query.keyword)
        val page = placeRepository.findAll(
            spec,
            PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by(Sort.Direction.DESC, "createdAt")),
        )

        val summaries = page.content.map { it.toSummaryResponse(query.lat, query.lng) }
        // 평점순/거리순은 조회된 페이지 안에서만 재정렬한다. 전체 정렬이 필요해지면
        // 평점 집계·거리 계산을 DB 쿼리로 내려야 한다.
        val sorted = when (query.sort) {
            PlaceSort.RECENT -> summaries
            PlaceSort.RATING -> summaries.sortedByDescending { it.rating.average }
            PlaceSort.DISTANCE -> summaries.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }

        return PageResponse(sorted, page.number, page.size, page.totalElements, page.totalPages)
    }

    override fun get(placeId: Long): PlaceResponse = findPlace(placeId).toResponse()

    @Transactional
    override fun create(ownerId: Long, request: PlaceCreateRequest): PlaceResponse {
        val owner = userRepository.findById(ownerId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val place = Place(
            owner = owner,
            name = request.name,
            category = request.category,
            address = request.address,
            roadAddress = request.roadAddress,
            externalLink = request.externalLink,
            latitude = request.latitude,
            longitude = request.longitude,
            memo = request.memo,
        )
        place.tags = tagService.findOrCreateAll(request.tags).toMutableSet()
        return placeRepository.save(place).toResponse()
    }

    @Transactional
    override fun update(placeId: Long, ownerId: Long, request: PlaceUpdateRequest): PlaceResponse {
        val place = findPlace(placeId)
        requireOwner(place, ownerId)

        place.name = request.name
        place.category = request.category
        place.address = request.address
        place.roadAddress = request.roadAddress
        place.externalLink = request.externalLink
        place.latitude = request.latitude
        place.longitude = request.longitude
        place.memo = request.memo
        place.tags = tagService.findOrCreateAll(request.tags).toMutableSet()

        return place.toResponse()
    }

    @Transactional
    override fun delete(placeId: Long, ownerId: Long) {
        val place = findPlace(placeId)
        requireOwner(place, ownerId)
        placeRepository.delete(place)
    }

    private fun findPlace(placeId: Long): Place =
        placeRepository.findById(placeId).orElseThrow { ApiException(ErrorCode.PLACE_NOT_FOUND) }

    private fun requireOwner(place: Place, userId: Long) {
        if (place.owner.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun ratingSummaryOf(placeId: Long) =
        RatingSummary(
            average = ratingRepository.averageScore(placeId).roundTo2Decimals(),
            count = ratingRepository.countByPlaceId(placeId),
        )

    private fun Place.toResponse(): PlaceResponse {
        val placeId = requireNotNull(id)
        return PlaceResponse(
            id = placeId,
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            roadAddress = roadAddress,
            externalLink = externalLink,
            latitude = latitude,
            longitude = longitude,
            memo = memo,
            photos = photoRepository.findByPlaceIdOrderByCreatedAtAsc(placeId)
                .map { PhotoResponse(requireNotNull(it.id), photoStorageService.resolveUrl(it.storageKey)) },
            rating = ratingSummaryOf(placeId),
            owner = owner.toResponse(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun Place.toSummaryResponse(lat: Double?, lng: Double?): PlaceSummaryResponse {
        val placeId = requireNotNull(id)
        return PlaceSummaryResponse(
            id = placeId,
            name = name,
            category = category,
            tags = tags.map { it.name }.sorted(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            memo = memo,
            thumbnailUrl = photoRepository.findByPlaceIdOrderByCreatedAtAsc(placeId)
                .firstOrNull()
                ?.let { photoStorageService.resolveUrl(it.storageKey) },
            rating = ratingSummaryOf(placeId),
            distanceKm = if (lat != null && lng != null) {
                haversineKm(lat, lng, latitude, longitude).roundTo2Decimals()
            } else {
                null
            },
            createdAt = createdAt,
        )
    }
}
