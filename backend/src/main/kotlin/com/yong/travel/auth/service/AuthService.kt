package com.yong.travel.auth.service

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.springframework.stereotype.Service

interface AuthService {
    fun getCurrentUser(userId: Long): UserResponse
}

@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
) : AuthService {

    override fun getCurrentUser(userId: Long): UserResponse =
        userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
            .toResponse()
}
