package com.yong.travel.auth.service

import com.yong.travel.auth.dto.MeResponse
import com.yong.travel.auth.dto.toMeResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getCurrentUser(userId: Long): MeResponse =
        userRepository.findById(userId)
            .orElseThrow {
                // 세션은 살아 있는데 사용자 행이 없는 경우다. 개발 중 DB 를 새로 만들면 자주 마주친다.
                log.debug("본인 정보 조회 실패 userId={} 사유=사용자_없음", userId)
                ApiException(ErrorCode.UNAUTHENTICATED)
            }
            .toMeResponse()
}
