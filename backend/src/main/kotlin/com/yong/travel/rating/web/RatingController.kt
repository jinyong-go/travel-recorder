package com.yong.travel.rating.web

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.web.requireLogin
import com.yong.travel.rating.dto.RatingRequest
import com.yong.travel.rating.dto.RatingResponse
import com.yong.travel.rating.service.RatingService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/places/{placeId}/ratings")
class RatingController(
    private val ratingService: RatingService,
) {

    @GetMapping("/me")
    fun getMine(
        @PathVariable placeId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): RatingResponse? = ratingService.getMine(placeId, requireLogin(principal))

    @PutMapping
    fun upsert(
        @PathVariable placeId: Long,
        @RequestBody @Valid request: RatingRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): RatingResponse = ratingService.upsert(placeId, requireLogin(principal), request)

    @DeleteMapping
    fun delete(
        @PathVariable placeId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        ratingService.delete(placeId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
