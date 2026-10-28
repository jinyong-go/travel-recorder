package com.yong.travel.tag.service

import com.yong.travel.tag.domain.Tag
import com.yong.travel.tag.persistence.TagEntity
import com.yong.travel.tag.persistence.TagRepository
import com.yong.travel.tag.persistence.TagSpecifications
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 태그 자동완성과 태그 생성을 맡는다. */
@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 볼 수 있는 기록에 쓰인 태그 중에서 자동완성 후보를 이름순으로 찾는다.
     *
     * @param keyword 비었으면 전체에서 고른다
     * @param userId 비로그인이면 null
     */
    fun search(keyword: String?, userId: Long?): List<Tag> {
        val spec = TagSpecifications.usedInRecordVisibleTo(userId).and(TagSpecifications.nameContains(keyword))
        // 전체 건수가 필요 없어 페이지 대신 상한만 건다.
        val tags = tagRepository.findBy<TagEntity, List<TagEntity>>(spec) {
            it.sortBy(Sort.by("name")).limit(AUTOCOMPLETE_LIMIT).all()
        }
        log.debug("태그 자동완성 userId={} count={}", userId, tags.size)
        return tags.map { Tag(requireNotNull(it.id), it.name) }
    }

    /**
     * 이름으로 태그를 찾고, 없으면 만든다. 기록에 연결하도록 엔티티를 돌려준다.
     *
     * @param names 앞뒤 공백을 지우고 빈 이름과 중복은 무시한다
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
        /** 자동완성 결과 상한. */
        const val AUTOCOMPLETE_LIMIT = 20
    }
}
