package com.yong.travel.auth.presentation

import com.yong.travel.auth.domain.LoginHistoryEntry
import java.time.Instant

/** 내 로그인 이력 한 건. 본인에게만 나가므로 IP 와 User-Agent 를 그대로 싣는다. */
data class LoginHistoryResponse(
    val id: Long,
    val loggedInAt: Instant,
    val ipAddress: String,
    val userAgent: String?,
) {
    companion object {
        fun from(entry: LoginHistoryEntry) =
            LoginHistoryResponse(entry.id, entry.loggedInAt, entry.ipAddress, entry.userAgent)
    }
}
