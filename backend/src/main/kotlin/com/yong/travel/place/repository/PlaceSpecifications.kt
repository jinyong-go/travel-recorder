package com.yong.travel.place.repository

import com.yong.travel.place.domain.Category
import com.yong.travel.place.domain.Place
import com.yong.travel.tag.domain.Tag
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object PlaceSpecifications {

    fun withFilters(category: Category?, tag: String?, keyword: String?): Specification<Place> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            category?.let { predicates.add(cb.equal(root.get<Category>("category"), it)) }

            keyword?.takeIf { it.isNotBlank() }?.let {
                val like = "%${it.trim()}%"
                predicates.add(
                    cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("address"), like),
                    ),
                )
            }

            tag?.takeIf { it.isNotBlank() }?.let {
                query?.distinct(true)
                val tagJoin = root.join<Place, Tag>("tags")
                predicates.add(cb.equal(tagJoin.get<String>("name"), it.trim()))
            }

            cb.and(*predicates.toTypedArray())
        }
}
