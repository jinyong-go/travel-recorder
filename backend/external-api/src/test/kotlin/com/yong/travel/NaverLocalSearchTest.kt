package com.yong.travel

import com.sun.net.httpserver.HttpServer
import com.yong.travel.place.client.NaverLocalSearchClient
import com.yong.travel.place.config.NaverSearchClientConfig
import com.yong.travel.place.config.NaverSearchProperties
import com.yong.travel.place.service.PlaceSearchService
import io.micrometer.observation.ObservationRegistry
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** 지역 검색 응답 변환을 확인한다. 실제 API 대신 같은 동작을 하는 로컬 서버를 쓴다. */
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
        assertEquals(2, service.search("카페", 0).content.size)
    }

    @Test
    fun `장소 이름의 태그를 지우고 엔티티를 복원한다`() {
        assertEquals("A&B 카페", service.search("카페", 0).content.first().name)
    }

    @Test
    fun `빈 링크와 전화번호는 null 이다`() {
        val place = service.search("카페", 0).content.first()

        assertNull(place.link)
        assertNull(place.telephone)
    }

    @Test
    fun `좌표를 읽을 수 없는 항목은 결과에서 빠진다`() {
        val result = service.search("카페", 0)

        assertFalse(result.content.any { it.name == "좌표 없음" })
        assertEquals(2L, result.totalElements)
        assertEquals(37.5026235, result.content.first().latitude, 1e-9)
    }

    @Test
    fun `같은 주소라도 이름이 다르면 둘 다 남고 이름과 주소가 모두 같으면 하나만 남는다`() {
        val names = service.search("카페", 0).content.map { it.name }

        assertEquals(listOf("A&B 카페", "같은 건물 다른 가게"), names)
    }

    companion object {
        /**
         * 실제 응답을 줄인 표본. 좌표가 잘못된 항목, 주소만 같은 다른 가게, 강조 태그만 다른 중복을 담았다.
         */
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
                },
                {
                  "title": "같은 건물 다른 가게",
                  "address": "서울특별시 강남구 역삼동 618-18",
                  "mapx": "1270279481",
                  "mapy": "375026235"
                },
                {
                  "title": "A&amp;B 카페",
                  "address": "서울특별시 강남구 역삼동 618-18",
                  "mapx": "1270279481",
                  "mapy": "375026235"
                }
              ]
            }
        """.trimIndent()
    }
}
