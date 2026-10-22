package com.yong.travel.auth.service

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toProfile
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 로그인한 본인의 정보 조회. 이메일까지 담기는 유일한 조회 경로다. */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인한 본인의 정보를 조회한다.
     *
     * @param userId 세션이 가리키는 사용자 id
     * @return 이메일까지 포함한 본인 정보
     * @throws ApiException `UNAUTHENTICATED` — 세션은 살아 있는데 사용자 행이 없을 때
     */
    fun getCurrentUser(userId: Long): MyProfile =
        userRepository.findById(userId)
            .orElseThrow {
                // 세션은 살아 있는데 사용자 행이 없는 경우다. 개발 중 DB 를 새로 만들면 자주 마주친다.
                log.debug("본인 정보 조회 실패: 사용자 없음 userId={}", userId)
                ApiException(ErrorCode.UNAUTHENTICATED)
            }
            .toProfile()
}
