package com.yong.travel

import com.sun.net.httpserver.HttpServer
import com.yong.travel.search.client.NaverLocalSearchClient
import com.yong.travel.search.config.NaverSearchClientConfig
import com.yong.travel.search.config.NaverSearchProperties
import com.yong.travel.search.service.PlaceSearchService
import io.micrometer.observation.ObservationRegistry
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 지역 검색 응답 변환 검증. 실제 API 대신 JDK HttpServer 로 네이버의 동작을 흉내 낸다 —
 * Accept 에 JSON 이 없으면 JSON 본문에 text/plain 을 붙여 보낸다.
 *
 * 클라이언트는 운영과 같은 설정(NaverSearchClientConfig)으로 만든다. 설정에서 Accept 가 빠지면
 * 응답 변환이 실패해 모든 테스트가 깨진다.
 */
class NaverLocalSearchTest {

    private lateinit var server: HttpServer
    private lateinit var service: PlaceSearchService

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/search/v1/local") { exchange ->
            val acceptsJson = exchange.requestHeaders.getFirst("Accept").orEmpty().contains("application/json")
            val contentType = if (acceptsJson) "application/json;charset=UTF-8" else "text/plain;charset=UTF-8"
            val body = SAMPLE.toByteArray()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val properties = NaverSearchProperties().apply {
            baseUrl = "http://localhost:${server.address.port}/search/v1/local"
        }
        val restClient = NaverSearchClientConfig().naverSearchRestClient(properties, ObservationRegistry.NOOP)
        service = PlaceSearchService(NaverLocalSearchClient(restClient))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `Accept 를 JSON 으로 보내 text-plain 응답 없이 변환된다`() {
        assertEquals(1, service.search("카페", 0).content.size)
    }

    @Test
    fun `장소 이름의 태그를 지우고 엔티티를 복원한다`() {
        assertEquals("A&B 카페", service.search("카페", 0).content.single().name)
    }

    @Test
    fun `빈 링크와 전화번호는 null 이다`() {
        val place = service.search("카페", 0).content.single()

        assertNull(place.link)
        assertNull(place.telephone)
    }

    @Test
    fun `좌표를 읽을 수 없는 항목은 결과에서 빠진다`() {
        val result = service.search("카페", 0)

        assertEquals(1, result.content.size)
        assertEquals(1L, result.totalElements)
        assertEquals(37.5026235, result.content.single().latitude, 1e-9)
    }

    companion object {
        /** 실제 응답 표본을 줄인 것. 두 번째 항목은 좌표가 숫자가 아니다. */
        private val SAMPLE = """
            {
              "total": 2,
              "items": [
                {
                  "title": "A&amp;<b>B</b> 카페",
                  "link": "",
                  "category": "음식점>카페,디저트",
                  "telephone": "",
                  "address": "서울특별시 강남구 역삼동 618-18",
                  "roadAddress": "서울특별시 강남구 봉은사로6길 39",
                  "mapx": "1270279481",
                  "mapy": "375026235"
                },
                {
                  "title": "좌표 없음",
                  "address": "서울특별시 강남구 역삼동 825-8",
                  "mapx": "",
                  "mapy": ""
                }
              ]
            }
        """.trimIndent()
    }
}
