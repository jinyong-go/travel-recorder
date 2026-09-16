package com.yong.travel.review.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.review.dto.ReviewRequest
import com.yong.travel.review.dto.ReviewResponse
import com.yong.travel.review.service.ReviewService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 리뷰는 사용자당 여행지 1건이므로, 수정·삭제 경로에 리뷰 ID 를 받지 않고
 * `(여행지, 로그인 사용자)` 조합으로 대상을 특정한다.
 */
@RestController
@RequestMapping("/api/places/{placeId}/reviews")
class ReviewController(
    private val reviewService: ReviewService,
) {

    @GetMapping
    fun list(
        @PathVariable placeId: Long,
        @PageableDefault(size = 10) pageable: Pageable,
    ): PageResponse<ReviewResponse> = reviewService.list(placeId, pageable)

    @GetMapping("/me")
    fun getMine(
        @PathVariable placeId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<ReviewResponse> =
        reviewService.getMine(placeId, requireLogin(principal))
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @PutMapping
    fun upsert(
        @PathVariable placeId: Long,
        @RequestBody @Valid request: ReviewRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ReviewResponse = reviewService.upsert(placeId, requireLogin(principal), request)

    @DeleteMapping
    fun delete(
        @PathVariable placeId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        reviewService.delete(placeId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
