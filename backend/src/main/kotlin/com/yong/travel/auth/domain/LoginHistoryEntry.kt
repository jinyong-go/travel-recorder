package com.yong.travel.auth.domain

import java.time.Instant

/** 성공한 로그인 한 건. 본인에게만 나간다. */
data class LoginHistoryEntry(
    val id: Long,
    val ipAddress: String,

    /** User-Agent 원문. 헤더가 없던 요청이면 null 이다. 해석은 화면의 몫이다. */
    val userAgent: String?,

    val loggedInAt: Instant,
)
