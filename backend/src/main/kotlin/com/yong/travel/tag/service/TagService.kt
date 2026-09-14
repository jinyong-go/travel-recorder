package com.yong.travel.tag.service

import com.yong.travel.tag.domain.Tag
import com.yong.travel.tag.dto.TagResponse
import com.yong.travel.tag.repository.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface TagService {
    fun search(keyword: String?): List<TagResponse>

    /** 여행지 등록/수정 시 새 태그명은 생성하고 기존 태그는 재사용한다. */
    fun findOrCreateAll(names: List<String>): Set<Tag>
}

@Service
@Transactional(readOnly = true)
class TagServiceImpl(
    private val tagRepository: TagRepository,
) : TagService {

    override fun search(keyword: String?): List<TagResponse> {
        val tags = if (keyword.isNullOrBlank()) {
            tagRepository.findAll()
        } else {
            tagRepository.findByNameContainingIgnoreCase(keyword.trim())
        }
        return tags.map { TagResponse(requireNotNull(it.id), it.name) }
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
}
