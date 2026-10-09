package com.yong.travel.auth.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 성공한 로그인의 기록. 읽기는 본인 것뿐이며, 조건이 곧 본인 필터다.
 *
 * 지우는 메서드는 보관 기간 정리 배치를 만들 때 함께 둔다.
 */
interface LoginHistoryRepository : JpaRepository<LoginHistoryEntity, Long> {

    /** 응답에 사용자를 싣지 않으므로 fetch join 이 필요 없다. */
    fun findByUserIdOrderByLoggedInAtDesc(userId: Long, pageable: Pageable): Page<LoginHistoryEntity>
}
