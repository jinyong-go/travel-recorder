package com.yong.travel

import com.yong.travel.common.error.ApiException
import com.yong.travel.place.client.NaverLocalSearchClient
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 장소 검색 호출이 지표로 남고 검색어는 남지 않는지 확인한다.
 * 외부 API 대신 닫힌 포트를 불러, 실패한 호출도 지표에 남는지 함께 본다.
 */
@SpringBootTest(properties = ["naver.api.base-url=http://localhost:1/search/v1/local"])
class PlaceSearchMetricsTest {

    @Autowired private lateinit var client: NaverLocalSearchClient
    @Autowired private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `외부 호출은 http_client_requests 로 남고 검색어는 어떤 라벨에도 없다`() {
        assertThrows<ApiException> { client.search(QUERY) }

        val meters = meterRegistry.find("http.client.requests").meters()
        assertTrue(meters.isNotEmpty(), "외부 호출 지표가 남아야 한다")
        val tagValues = meters.flatMap { meter -> meter.id.tags.map { it.value } }
        assertFalse(tagValues.any { it.contains(QUERY) }, "검색어가 라벨에 남았다: $tagValues")
    }

    companion object {
        private const val QUERY = "검색어노출확인"
    }
}
