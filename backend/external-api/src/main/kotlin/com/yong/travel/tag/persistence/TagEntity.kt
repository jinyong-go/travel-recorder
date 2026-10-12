package com.yong.travel.tag.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 태그. 이름이 유일하며 여러 기록이 함께 쓴다. */
@Entity
@Table(name = "tags", uniqueConstraints = [UniqueConstraint(columnNames = ["name"])])
class TagEntity(
    @Column(nullable = false)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
