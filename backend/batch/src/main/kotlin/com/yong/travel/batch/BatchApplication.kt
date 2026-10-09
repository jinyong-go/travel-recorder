package com.yong.travel.batch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

/**
 * 정기 정리 작업을 돌리는 배치 애플리케이션. 웹 서버 없이 잡을 실행하고 끝난다.
 *
 * 종료 코드를 잡 결과로 돌려준다. 스케줄러(cron 등)가 실패를 알아차릴 수단이 이것뿐이다.
 */
@SpringBootApplication
class BatchApplication

fun main(args: Array<String>) {
    exitProcess(SpringApplication.exit(runApplication<BatchApplication>(*args)))
}
