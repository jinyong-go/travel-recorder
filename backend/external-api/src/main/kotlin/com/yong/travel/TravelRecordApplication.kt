package com.yong.travel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** API 서버. */
@SpringBootApplication
class TravelRecordApplication

/** API 서버를 띄운다. */
fun main(args: Array<String>) {
    runApplication<TravelRecordApplication>(*args)
}
