package com.yong.travel.trip.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.group.domain.Group
import java.time.Instant
import java.time.LocalDate

/**
 * 여행 한 건. 서비스가 조회를 모두 끝낸 결과를 묶어 컨트롤러에 넘기는 경계다.
 *
 * **JPA 엔티티가 아니다.** 커버 사진 URL·기록 수·공유 그룹처럼 여러 조회를 합쳐야 나오는 값을
 * 이미 담고 있으며, 이 객체를 만든 뒤에는 추가 조회가 일어나지 않는다.
 */
data class TripDetail(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,

    /** 커버가 지정되지 않았으면 null. 서버는 대체 이미지를 고르지 않는다 (명세 §4.3.1). */
    val coverPhotoUrl: String?,

    /** 삭제되지 않은 하위 기록 수 (명세 §3.1). */
    val recordCount: Long,

    val owner: User,

    /**
     * 공개 범위. **소유자 본인의 조회에서만 채운다** (그 외에는 null).
     *
     * 응답에서 가리는 대신 아예 읽지 않는다 — 누구에게 공유했는지는 열람자에게 알릴 이유가 없고
     * (공통 명세 §3.5), 담아 두었다가 가리는 것보다 담지 않는 편이 확실하다.
     */
    val visibility: Visibility?,

    /** 공유 그룹. [visibility] 가 `GROUP` 인 소유자 조회에서만 채운다. 그 외에는 null. */
    val sharedGroups: List<Group>?,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isOwnedBy(userId: Long?): Boolean = userId != null && owner.id == userId
}
