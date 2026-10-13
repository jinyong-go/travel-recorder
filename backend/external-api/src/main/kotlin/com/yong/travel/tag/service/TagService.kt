package com.yong.travel.tag.service

import com.yong.travel.tag.domain.Tag
import com.yong.travel.tag.persistence.TagEntity
import com.yong.travel.tag.persistence.TagRepository
import com.yong.travel.tag.persistence.TagSpecifications
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 태그 자동완성과, 기록 저장 시 태그 이름을 태그 엔티티로 확보하는 일. */
@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 태그 자동완성 후보를 찾는다. 후보는 요청자가 볼 수 있는 기록에 쓰인 태그로 제한된다.
     *
     * @param keyword 부분 일치 검색어. 비었으면 전체에서 고른다
     * @param userId 요청자의 사용자 id. 비로그인이면 null
     * @return 이름순 태그 후보. 최대 20개
     */
    fun search(keyword: String?, userId: Long?): List<Tag> {
        val spec = TagSpecifications.usedInRecordVisibleTo(userId).and(TagSpecifications.nameContains(keyword))
        // 건수 쿼리가 필요 없는 후보 목록이라 페이지 대신 상한만 건다.
        val tags = tagRepository.findBy<TagEntity, List<TagEntity>>(spec) {
            it.sortBy(Sort.by("name")).limit(AUTOCOMPLETE_LIMIT).all()
        }
        // 자동완성도 볼 수 있는 기록의 태그로만 제한된다. 건수가 튀면 판정이 샌 것이다.
        log.debug("태그 자동완성 userId={} count={}", userId, tags.size)
        return tags.map { Tag(requireNotNull(it.id), it.name) }
    }

    /**
     * 기록 등록/수정 시 새 태그명은 생성하고 기존 태그는 재사용한다.
     *
     * 도메인 객체가 아니라 엔티티를 반환한다. 컨트롤러로 나가는 경계가 아니라 `TripRecordService`
     * 가 `TripRecordEntity.tags` 에 그대로 걸 연관을 받아 가는 서비스 간 호출이기 때문이다.
     *
     * @param names 태그 이름 목록. 앞뒤 공백을 지우고, 빈 이름과 중복은 무시한다
     * @return 이름마다 하나씩의 태그 엔티티
     */
    @Transactional
    fun findOrCreateAll(names: List<String>): Set<TagEntity> {
        val distinctNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return emptySet()

        val existing = tagRepository.findByNameIn(distinctNames)
        val existingNames = existing.map { it.name }.toSet()
        val created = tagRepository.saveAll(
            distinctNames.filterNot { it in existingNames }.map { TagEntity(name = it) },
        )
        log.debug("태그 확보 reused={} created={}", existing.size, created.size)
        return (existing + created).toSet()
    }

    private companion object {
        /** 자동완성 결과 상한. 후보라 그 이상은 쓰이지 않는다. */
        const val AUTOCOMPLETE_LIMIT = 20
    }
}
