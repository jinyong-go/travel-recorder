package com.yong.travel.group.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** 그룹 멤버 이력 저장소. 아직 조회하는 곳은 없다. */
interface GroupMemberHistoryRepository : JpaRepository<GroupMemberHistoryEntity, Long>
