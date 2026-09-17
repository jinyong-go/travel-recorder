package com.yong.travel.tag.service

import com.yong.travel.group.service.GroupService
import com.yong.travel.tag.domain.Tag
import com.yong.travel.tag.dto.TagResponse
import com.yong.travel.tag.repository.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface TagService {
    /** 자동완성 후보는 요청자가 볼 수 있는 기록에 쓰인 태그로 제한된다. */
    fun search(keyword: String?, userId: Long?): List<TagResponse>

    /** 기록 등록/수정 시 새 태그명은 생성하고 기존 태그는 재사용한다. */
    fun findOrCreateAll(names: List<String>): Set<Tag>
}

@Service
@Transactional(readOnly = true)
class TagServiceImpl(
    private val tagRepository: TagRepository,
    private val groupService: GroupService,
) : TagService {

    override fun search(keyword: String?, userId: Long?): List<TagResponse> {
        val groupIds = groupService.groupIdsOf(userId).ifEmpty { listOf(NO_MATCH) }
        return tagRepository
            .findVisibleTags(keyword?.trim()?.takeIf { it.isNotBlank() }, userId ?: NO_MATCH, groupIds)
            .map { TagResponse(requireNotNull(it.id), it.name) }
    }

    @Transactional
    override fun findOrCreateAll(names: List<String>): Set<Tag> {
        val distinctNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinctNames.isEmpty()) return emptySet()

        val existing = tagRepository.findByNameIn(distinctNames)
        val existingNames = existing.map { it.name }.toSet()
        val created = tagRepository.saveAll(
            distinctNames.filterNot { it in existingNames }.map { Tag(name = it) },
        )
        return (existing + created).toSet()
    }

    private companion object {
        /** 어떤 사용자·그룹 id 와도 일치하지 않는 값. JPQL 파라미터에 null 과 빈 목록을 넘기지 않으려고 쓴다. */
        const val NO_MATCH = -1L
    }
}
