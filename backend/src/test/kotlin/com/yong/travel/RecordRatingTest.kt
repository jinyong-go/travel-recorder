package com.yong.travel

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.VisitRecord
import com.yong.travel.record.dto.VisitRecordCreateRequest
import com.yong.travel.record.repository.VisitRecordRepository
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 평점은 0.5 ~ 5.0 을 0.5 단위로만 받는다.
 * 요청 DTO 의 Bean Validation 과 DB CHECK 제약 두 곳에서 모두 막히는지 확인한다.
 */
@SpringBootTest
@Transactional
class RecordRatingTest {

    @Autowired private lateinit var validator: Validator
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var recordRepository: VisitRecordRepository

    @Test
    fun `0_5 단위 값은 통과한다`() {
        val allowed = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)
        allowed.forEach { rating ->
            assertTrue(validator.validate(newRequest(rating)).isEmpty(), "$rating 은 허용되어야 한다")
        }
    }

    @Test
    fun `0_5 단위가 아니거나 범위를 벗어난 값은 거부된다`() {
        val rejected = listOf(3.7, 4.1, 0.25, 0.0, -1.0, 5.5)
        rejected.forEach { rating ->
            assertTrue(validator.validate(newRequest(rating)).isNotEmpty(), "$rating 은 거부되어야 한다")
        }
    }

    @Test
    fun `DTO 검증을 우회해도 DB CHECK 제약이 0_5 단위가 아닌 값을 막는다`() {
        val user = userRepository.save(
            User(provider = "naver", providerId = "provider-1", email = "t@example.com", name = "테스터"),
        )

        assertFailsWith<DataIntegrityViolationException> {
            recordRepository.saveAndFlush(
                VisitRecord(
                    author = user,
                    name = "테스트 기록",
                    category = Category.FOOD,
                    address = "서울시 어딘가",
                    latitude = 37.5,
                    longitude = 127.0,
                    rating = 3.7,
                ),
            )
        }
    }

    private fun newRequest(rating: Double) = VisitRecordCreateRequest(
        name = "테스트 기록",
        category = Category.FOOD,
        address = "서울시 어딘가",
        latitude = 37.5,
        longitude = 127.0,
        rating = rating,
    )
}
