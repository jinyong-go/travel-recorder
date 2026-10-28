package com.yong.travel.auth.security

/**
 * 로그인 주체의 사용자 id.
 * 로그인 수단이 바뀌어도 컨트롤러가 영향을 받지 않도록 공통 부분만 뽑았다.
 */
interface LoginUser {
    val userId: Long
}
