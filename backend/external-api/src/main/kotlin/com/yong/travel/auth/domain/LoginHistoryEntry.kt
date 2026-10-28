package com.yong.travel.auth.domain

import java.time.Instant

/** 성공한 로그인 한 건. 본인에게만 나간다. */
data class LoginHistoryEntry(
    val id: Long,
    val ipAddress: String,

    /** User-Agent 원문. 헤더가 없었으면 null. */
    val userAgent: String?,

    val loggedInAt: Instant,
)
