package com.yong.travel.auth.presentation

import com.yong.travel.auth.domain.LoginHistoryEntry
import java.time.Instant

/** 내 로그인 이력 한 건. 본인만 보므로 IP 와 User-Agent 를 그대로 싣는다. */
data class LoginHistoryResponse(
    val id: Long,
    val loggedInAt: Instant,
    val ipAddress: String,
    val userAgent: String?,
) {
    companion object {
        /** 로그인 이력 → 응답. */
        fun from(entry: LoginHistoryEntry) =
            LoginHistoryResponse(entry.id, entry.loggedInAt, entry.ipAddress, entry.userAgent)
    }
}
