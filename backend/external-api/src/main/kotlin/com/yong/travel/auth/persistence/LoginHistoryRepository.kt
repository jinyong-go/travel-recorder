package com.yong.travel.auth.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/** 로그인 이력 저장소. 보관 기간이 지난 이력은 배치가 지운다. */
interface LoginHistoryRepository : JpaRepository<LoginHistoryEntity, Long> {

    /** 한 사용자의 로그인 이력을 최신순으로 읽는다. */
    fun findByUserIdOrderByLoggedInAtDesc(userId: Long, pageable: Pageable): Page<LoginHistoryEntity>
}
