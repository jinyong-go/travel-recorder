package com.yong.travel.comment.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.comment.domain.Comment
import com.yong.travel.comment.dto.CommentRequest
import com.yong.travel.comment.dto.CommentResponse
import com.yong.travel.comment.repository.CommentRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.place.repository.PlaceRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface CommentService {
    fun list(placeId: Long, pageable: Pageable): PageResponse<CommentResponse>
    fun create(placeId: Long, authorId: Long, request: CommentRequest): CommentResponse
    fun delete(placeId: Long, commentId: Long, authorId: Long)
}

@Service
@Transactional(readOnly = true)
class CommentServiceImpl(
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val commentRepository: CommentRepository,
) : CommentService {

    override fun list(placeId: Long, pageable: Pageable): PageResponse<CommentResponse> {
        val page = commentRepository.findByPlaceId(
            placeId,
            PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return PageResponse.of(page.map { it.toResponse() })
    }

    @Transactional
    override fun create(placeId: Long, authorId: Long, request: CommentRequest): CommentResponse {
        val place = placeRepository.findById(placeId).orElseThrow { ApiException(ErrorCode.PLACE_NOT_FOUND) }
        val author = userRepository.findById(authorId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        return commentRepository.save(Comment(place = place, author = author, content = request.content)).toResponse()
    }

    @Transactional
    override fun delete(placeId: Long, commentId: Long, authorId: Long) {
        val comment = commentRepository.findByIdAndPlaceId(commentId, placeId)
            ?: throw ApiException(ErrorCode.COMMENT_NOT_FOUND)
        if (comment.author.id != authorId) throw ApiException(ErrorCode.FORBIDDEN)
        commentRepository.delete(comment)
    }

    private fun Comment.toResponse() =
        CommentResponse(
            id = requireNotNull(id),
            author = author.toResponse(),
            content = content,
            createdAt = createdAt,
        )
}
