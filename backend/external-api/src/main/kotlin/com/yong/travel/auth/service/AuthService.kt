package com.yong.travel.auth.service

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toProfile
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
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
                log.debug("본인 정보 조회 실패 userId={} 사유=사용자_없음", userId)
                ApiException(ErrorCode.UNAUTHENTICATED)
            }
            .toProfile()
}
