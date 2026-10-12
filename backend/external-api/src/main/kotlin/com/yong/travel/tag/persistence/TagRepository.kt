package com.yong.travel.tag.persistence

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 태그 조회. 자동완성은 요청자가 볼 수 있는 기록의 태그로만 제한한다. */
interface TagRepository : JpaRepository<TagEntity, Long> {

    /**
     * 이름이 일치하는 태그를 한 번에 읽는다.
     *
     * @param names 태그 이름 목록
     * @return 이미 있는 태그들. 없는 이름은 결과에서 빠진다
     */
    fun findByNameIn(names: Collection<String>): List<TagEntity>

    /**
     * 자동완성 후보를 "요청자가 볼 수 있는 기록에 쓰인 태그" 로 제한한다.
     * 전체 태그를 그대로 내려주면 비공개 기록에만 쓰인 태그가 그대로 드러난다.
     *
     * **판정 대상은 기록이 아니라 소속 여행이다** — 공개 범위가 여행에만 있으므로 여행을
     * 조인하지 않으면 판정할 근거가 없고, 조인 없는 조회 경로는 그대로 우회로가 된다.
     *
     * 파라미터에 null 을 넣지 않으려고 호출부에서 비로그인은 userId = -1,
     * 소속 그룹이 없으면 groupIds = [-1] 로 바꿔 넘긴다 (어느 행에도 매칭되지 않는 값).
     *
     * @param keywordPattern 이스케이프까지 마친 LIKE 패턴(`containsPattern`). escape 문자는 `LIKE_ESCAPE`
     *        와 같은 `\` 다. null 이면 이름 조건을 걸지 않는다
     * @param userId 요청자의 사용자 id. 비로그인이면 -1
     * @param groupIds 요청자가 속한 그룹 id 목록. 없으면 [-1]
     * @param limit 결과 건수 상한
     * @return 이름순 태그 목록
     */
    @Query(
        """
        select distinct t from TripRecordEntity r
        join r.trip p
        join r.tags t
        where (:keywordPattern is null or lower(t.name) like lower(:keywordPattern) escape '\')
          and (
            p.visibility = com.yong.travel.trip.domain.TripVisibility.PUBLIC
            or p.owner.id = :userId
            or (
              p.visibility = com.yong.travel.trip.domain.TripVisibility.GROUP
              and exists (
                select 1 from TripShareEntity s
                where s.trip = p and s.group.id in :groupIds
              )
            )
          )
        order by t.name asc
        """,
    )
    fun findVisibleTags(
        @Param("keywordPattern") keywordPattern: String?,
        @Param("userId") userId: Long,
        @Param("groupIds") groupIds: Collection<Long>,
        limit: Limit,
    ): List<TagEntity>
}
