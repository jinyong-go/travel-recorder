package com.yong.travel.batch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

/** 정기 작업을 돌리는 배치 애플리케이션. */
@SpringBootApplication
class BatchApplication

/** 잡 하나를 돌리고 결과를 종료 코드로 돌려준다. 스케줄러가 실패를 알아차릴 수 있게 한다. */
fun main(args: Array<String>) {
    exitProcess(SpringApplication.exit(runApplication<BatchApplication>(*args)))
}
