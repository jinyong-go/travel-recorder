package com.yong.travel.group.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 그룹 저장소. */
interface GroupRepository : JpaRepository<GroupEntity, Long> {

    /** 사용자가 소유한 그룹 수. */
    fun countByOwnerId(ownerId: Long): Long

    /**
     * 그룹을 잠그고 읽는다. 없으면 null.
     * 동시에 수락해 정원을 넘지 않도록 정원 확인과 멤버 추가 사이를 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupEntity g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): GroupEntity?
}
