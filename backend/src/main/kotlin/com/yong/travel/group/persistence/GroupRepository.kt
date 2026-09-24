package com.yong.travel.group.persistence

import com.yong.travel.group.persistence.GroupEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRepository : JpaRepository<GroupEntity, Long> {

    /**
     * 정원 검사와 멤버 입력을 한 트랜잭션에 묶기 위해 그룹 행을 잠그고 읽는다.
     *
     * 정원을 강제하는 DB 제약이 없어 애플리케이션 검사가 유일한 관문인데, 검사와 입력 사이에
     * 다른 수락이 끼어들면 6명짜리 그룹이 만들어진다 (명세 §4.8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupEntity g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): GroupEntity?
}
