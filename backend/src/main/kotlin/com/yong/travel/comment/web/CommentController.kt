package com.yong.travel.comment.web

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.comment.dto.CommentRequest
import com.yong.travel.comment.dto.CommentResponse
import com.yong.travel.comment.service.CommentService
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.requireLogin
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/places/{placeId}/comments")
class CommentController(
    private val commentService: CommentService,
) {

    @GetMapping
    fun list(
        @PathVariable placeId: Long,
        @PageableDefault(size = 10) pageable: Pageable,
    ): PageResponse<CommentResponse> = commentService.list(placeId, pageable)

    @PostMapping
    fun create(
        @PathVariable placeId: Long,
        @RequestBody @Valid request: CommentRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): CommentResponse = commentService.create(placeId, requireLogin(principal), request)

    @DeleteMapping("/{commentId}")
    fun delete(
        @PathVariable placeId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        commentService.delete(placeId, commentId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
