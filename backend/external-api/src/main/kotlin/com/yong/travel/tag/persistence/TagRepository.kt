package com.yong.travel.tag.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** 태그 조회. 자동완성은 [TagSpecifications] 로 요청자가 볼 수 있는 기록의 태그로만 제한한다. */
interface TagRepository : JpaRepository<TagEntity, Long>, JpaSpecificationExecutor<TagEntity> {

    /**
     * 이름이 일치하는 태그를 한 번에 읽는다.
     *
     * @return 이미 있는 태그들. 없는 이름은 결과에서 빠진다
     */
    fun findByNameIn(names: Collection<String>): List<TagEntity>
}
