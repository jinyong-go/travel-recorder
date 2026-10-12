package com.yong.travel.auth.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 성공한 로그인의 기록. 읽기는 본인 것뿐이며, 조건이 곧 본인 필터다.
 *
 * 지우는 메서드를 두지 않는다 — 보관 기간 정리는 batch 모듈이 SQL 로 한다.
 */
interface LoginHistoryRepository : JpaRepository<LoginHistoryEntity, Long> {

    /**
     * 한 사용자의 로그인 이력을 최신순으로 읽는다. 응답에 사용자를 싣지 않으므로 fetch join 이 필요 없다.
     *
     * @param userId 사용자 id
     * @param pageable 페이지 요청
     * @return 로그인 이력 한 페이지
     */
    fun findByUserIdOrderByLoggedInAtDesc(userId: Long, pageable: Pageable): Page<LoginHistoryEntity>
}
