package com.yong.travel.tag.repository

import com.yong.travel.tag.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TagRepository : JpaRepository<Tag, Long> {

    fun findByNameIn(names: Collection<String>): List<Tag>

    /**
     * 자동완성 후보를 "요청자가 볼 수 있는 기록에 쓰인 태그" 로 제한한다.
     * 전체 태그를 그대로 내려주면 비공개 기록에만 쓰인 태그가 그대로 드러난다.
     *
     * 파라미터에 null 을 넣지 않으려고 호출부에서 비로그인은 userId = -1,
     * 소속 그룹이 없으면 groupIds = [-1] 로 바꿔 넘긴다 (어느 행에도 매칭되지 않는 값).
     */
    @Query(
        """
        select distinct t from VisitRecord r
        join r.tags t
        where (:keyword is null or lower(t.name) like lower(concat('%', :keyword, '%')))
          and (
            r.visibility = com.yong.travel.record.domain.Visibility.PUBLIC
            or r.author.id = :userId
            or (
              r.visibility = com.yong.travel.record.domain.Visibility.GROUP
              and exists (
                select 1 from VisitRecordShare s
                where s.record = r and s.group.id in :groupIds
              )
            )
          )
        order by t.name asc
        """,
    )
    fun findVisibleTags(
        @Param("keyword") keyword: String?,
        @Param("userId") userId: Long,
        @Param("groupIds") groupIds: Collection<Long>,
    ): List<Tag>
}
