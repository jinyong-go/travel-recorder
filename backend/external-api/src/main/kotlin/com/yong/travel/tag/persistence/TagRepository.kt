package com.yong.travel.tag.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** 태그 저장소. */
interface TagRepository : JpaRepository<TagEntity, Long>, JpaSpecificationExecutor<TagEntity> {

    /** 이름이 일치하는 태그를 읽는다. 없는 이름은 빠진다. */
    fun findByNameIn(names: Collection<String>): List<TagEntity>
}
