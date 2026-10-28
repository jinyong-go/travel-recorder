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

/** 로그인 이력을 남기고 조회한다. 본인이 모르는 로그인을 알아차리게 한다. */
@Service
@Transactional(readOnly = true)
class LoginHistoryService(
    private val loginHistoryRepository: LoginHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 성공을 기록한다. 기록이 실패하면 로그인도 실패하도록 세션 발급 전에 부른다.
     *
     * @param userAgent 길면 잘라 저장한다
     */
    @Transactional
    fun record(userId: Long, ipAddress: String, userAgent: String?) {
        val saved = loginHistoryRepository.save(
            LoginHistoryEntity(
                user = userRepository.getReferenceById(userId),
                ipAddress = ipAddress,
                userAgent = userAgent?.take(LoginHistoryEntity.USER_AGENT_MAX_LENGTH),
            ),
        )
        // IP·User-Agent 는 보관 기간이 있는 값이라 로그에 남기지 않는다.
        log.debug("로그인 이력 기록 userId={} historyId={}", userId, saved.id)
    }

    /**
     * 내 로그인 이력을 최신순으로 조회한다.
     *
     * @param page 0부터 시작
     */
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
