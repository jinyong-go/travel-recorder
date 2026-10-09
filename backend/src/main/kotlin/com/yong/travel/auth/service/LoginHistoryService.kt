package com.yong.travel.auth.service

import com.yong.travel.auth.domain.LoginHistoryEntry
import com.yong.travel.auth.persistence.LoginHistoryEntity
import com.yong.travel.auth.persistence.LoginHistoryRepository
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인 이력의 기록과 조회. 본인이 모르는 로그인을 알아차리게 하는 것이 목적이다.
 */
@Service
@Transactional(readOnly = true)
class LoginHistoryService(
    private val loginHistoryRepository: LoginHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 성공을 기록한다. 세션을 발급하기 **전에** 부른다.
     *
     * 실패하면 예외가 그대로 올라가 로그인도 실패한다. 이력 없이 로그인이 성립하면 목록에 빈틈이
     * 생기고, 그 빈틈은 본인이 볼 때 드러나지 않는다.
     *
     * @param userAgent `User-Agent` 헤더 원문. 길면 잘라 저장하고, 없으면 null 로 둔다.
     */
    @Transactional
    fun record(userId: Long, ipAddress: String, userAgent: String?) {
        // 인증을 막 통과한 사용자라 행이 있다. 존재 확인 조회를 따로 하지 않고 FK 가 보장하게 둔다.
        val saved = loginHistoryRepository.save(
            LoginHistoryEntity(
                user = userRepository.getReferenceById(userId),
                ipAddress = ipAddress,
                userAgent = userAgent?.take(LoginHistoryEntity.USER_AGENT_MAX_LENGTH),
            ),
        )
        // IP·User-Agent 는 로그에 남기지 않는다. 보관 기간을 둔 값을 로그가 기한 없이 들고 있게 된다.
        log.debug("로그인 이력 기록 userId={} historyId={}", userId, saved.id)
    }

    /** 내 로그인 이력, 최신순. 대상은 언제나 요청자 본인이다. */
    fun list(userId: Long, page: Int): PageResult<LoginHistoryEntry> =
        loginHistoryRepository.findByUserIdOrderByLoggedInAtDesc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    private fun LoginHistoryEntity.toDomain() = LoginHistoryEntry(
        id = requireNotNull(id),
        ipAddress = ipAddress,
        userAgent = userAgent,
        loggedInAt = loggedInAt,
    )
}
