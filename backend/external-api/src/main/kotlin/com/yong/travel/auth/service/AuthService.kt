package com.yong.travel.auth.service

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toProfile
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 로그인한 본인의 정보를 조회한다. */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이메일을 포함한 본인 정보를 조회한다.
     *
     * @throws ApiException `UNAUTHENTICATED` — 세션의 사용자가 없을 때
     */
    fun getCurrentUser(userId: Long): MyProfile =
        userRepository.findById(userId)
            .orElseThrow {
                log.debug("본인 정보 조회 실패: 사용자 없음 userId={}", userId)
                ApiException(ErrorCode.UNAUTHENTICATED)
            }
            .toProfile()
}
