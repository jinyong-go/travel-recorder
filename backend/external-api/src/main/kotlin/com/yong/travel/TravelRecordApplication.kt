package com.yong.travel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 프론트엔드가 부르는 API 서버의 진입점. */
@SpringBootApplication
class TravelRecordApplication

/**
 * API 서버를 띄운다.
 *
 * @param args 명령행 인자. Spring 설정 덮어쓰기(`--spring.profiles.active=…`)에 쓴다
 */
fun main(args: Array<String>) {
    runApplication<TravelRecordApplication>(*args)
}
