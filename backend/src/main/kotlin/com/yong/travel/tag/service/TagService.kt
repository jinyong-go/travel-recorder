package com.yong.travel.tag.service

import com.yong.travel.group.service.GroupService
import com.yong.travel.tag.domain.Tag
import com.yong.travel.tag.dto.TagResponse
import com.yong.travel.tag.repository.TagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
    private val groupService: GroupService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 자동완성 후보는 요청자가 볼 수 있는 기록에 쓰인 태그로 제한된다. */
    fun search(keyword: String?, userId: Long?): List<TagResponse> {
        val groupIds = groupService.groupIdsOf(userId).ifEmpty { listOf(NO_MATCH) }
        val tags = tagRepository
            .findVisibleTags(keyword?.trim()?.takeIf { it.isNotBlank() }, userId ?: NO_MATCH, groupIds)
        // 자동완성도 볼 수 있는 기록의 태그로만 제한된다. 건수가 튀면 판정이 샌 것이다 (명세 §7).
        log.debug("태그 자동완성 userId={} 건수={}", userId, tags.size)
        return tags.map { TagResponse(requireNotNull(it.id), it.name) }
    }

    /** 기록 등록/수정 시 새 태그명은 생성하고 기존 태그는 재사용한다. */
    @Transactional
    fun findOrCreateAll(names: List<String>): Set<Tag> {
        val distinctNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return emptySet()

        val existing = tagRepository.findByNameIn(distinctNames)
        val existingNames = existing.map { it.name }.toSet()
        val created = tagRepository.saveAll(
            distinctNames.filterNot { it in existingNames }.map { Tag(name = it) },
        )
        log.debug("태그 확보 재사용={}건 신규={}건", existing.size, created.size)
        return (existing + created).toSet()
    }

    private companion object {
        /** 어떤 사용자·그룹 id 와도 일치하지 않는 값. JPQL 파라미터에 null 과 빈 목록을 넘기지 않으려고 쓴다. */
        const val NO_MATCH = -1L
    }
}
