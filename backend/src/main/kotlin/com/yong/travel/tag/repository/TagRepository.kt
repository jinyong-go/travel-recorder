package com.yong.travel.tag.repository

import com.yong.travel.tag.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository

interface TagRepository : JpaRepository<Tag, Long> {
    fun findByNameIn(names: Collection<String>): List<Tag>
    fun findByNameContainingIgnoreCase(keyword: String): List<Tag>
}
