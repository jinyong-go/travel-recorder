package com.yong.travel.auth.security

/**
 * 로그인 주체가 우리 DB 의 `User` PK 를 갖고 있다는 사실만 나타낸다.
 *
 * 로그인 수단마다 principal 구현 타입이 다르다 — 인메모리 로그인은 `UserDetails`,
 * 네이버 OAuth 는 `OAuth2User` 다. 컨트롤러가 수단을 직접 받으면 수단을 바꿀 때마다
 * 모든 컨트롤러 시그니처가 따라 바뀌므로, 공통으로 필요한 것만 여기로 분리한다 (명세 §2.1).
 */
interface LoginUser {
    val userId: Long
}
