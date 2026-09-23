package com.yong.travel.tag.persistence

import com.yong.travel.tag.persistence.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TagRepository : JpaRepository<Tag, Long> {

    fun findByNameIn(names: Collection<String>): List<Tag>

    /**
     * 자동완성 후보를 "요청자가 볼 수 있는 기록에 쓰인 태그" 로 제한한다.
     * 전체 태그를 그대로 내려주면 비공개 기록에만 쓰인 태그가 그대로 드러난다.
     *
     * **판정 대상은 기록이 아니라 소속 여행이다** — 공개 범위가 여행에만 있으므로 여행을
     * 조인하지 않으면 판정할 근거가 없고, 조인 없는 조회 경로는 그대로 우회로가 된다.
     *
     * 파라미터에 null 을 넣지 않으려고 호출부에서 비로그인은 userId = -1,
     * 소속 그룹이 없으면 groupIds = [-1] 로 바꿔 넘긴다 (어느 행에도 매칭되지 않는 값).
     */
    @Query(
        """
        select distinct t from TripRecord r
        join r.trip p
        join r.tags t
        where (:keyword is null or lower(t.name) like lower(concat('%', :keyword, '%')))
          and (
            p.visibility = com.yong.travel.trip.domain.Visibility.PUBLIC
            or p.owner.id = :userId
            or (
              p.visibility = com.yong.travel.trip.domain.Visibility.GROUP
              and exists (
                select 1 from TripShare s
                where s.trip = p and s.group.id in :groupIds
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
