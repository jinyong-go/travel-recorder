package com.yong.travel.record.domain

/**
 * 방문 기록의 공개 범위. 기본값은 PRIVATE 이며, 작성자가 명시적으로 넓히지 않는 한 공개되지 않는다.
 *
 * GROUP 은 "공유된 그룹의 멤버만" 이라는 뜻이라, 공유 그룹이 하나도 없으면 결과적으로 PRIVATE 과 같다.
 * 그 상태를 막지 않는 이유는 "그룹 공유"를 먼저 고르고 그룹을 나중에 붙이는 순서가 자연스럽기 때문이며,
 * 대신 화면이 "선택한 그룹이 없어 나만 볼 수 있습니다" 로 알린다.
 */
enum class Visibility {
    PRIVATE,
    GROUP,
    PUBLIC,
}
