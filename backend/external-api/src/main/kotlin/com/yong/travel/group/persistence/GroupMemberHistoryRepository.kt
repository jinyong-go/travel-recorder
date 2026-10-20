package com.yong.travel.group.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 그룹 멤버 이력. 조회 경로가 아직 없어 저장만 한다. 지우는 메서드를 두지 않는다. */
interface GroupMemberHistoryRepository : JpaRepository<GroupMemberHistoryEntity, Long>
