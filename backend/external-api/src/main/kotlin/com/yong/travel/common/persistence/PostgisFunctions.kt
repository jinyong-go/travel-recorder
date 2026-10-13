package com.yong.travel.common.persistence

import org.hibernate.boot.model.FunctionContributions
import org.hibernate.boot.model.FunctionContributor
import org.hibernate.type.StandardBasicTypes

/**
 * PostGIS 거리 연산을 HQL·Criteria 에서 부를 수 있게 등록한다.
 *
 * `<->`(KNN 거리)는 함수가 아니라 연산자라 `cb.function` 으로 바로 부를 수 없어 패턴 함수로 감싼다.
 * 렌더링되는 식은 trip_records 의 위치 인덱스 표현식과 같아야 한다 — 다르면 인덱스를 타지 않고
 * 전체 정렬로 떨어진다.
 *
 * META-INF/services 의 등록 파일로 Hibernate 가 찾는다.
 */
class PostgisFunctions : FunctionContributor {

    /**
     * 함수 레지스트리에 `knn_distance` 를 등록한다.
     *
     * @param contributions Hibernate 가 넘기는 함수·타입 레지스트리
     */
    override fun contributeFunctions(contributions: FunctionContributions) {
        val doubleType = contributions.typeConfiguration.basicTypeRegistry.resolve(StandardBasicTypes.DOUBLE)
        contributions.functionRegistry
            .patternDescriptorBuilder(
                KNN_DISTANCE,
                "(ST_SetSRID(ST_MakePoint(?2, ?1), 4326)::geography <-> ST_SetSRID(ST_MakePoint(?4, ?3), 4326)::geography)",
            )
            .setExactArgumentCount(4)
            .setInvariantType(doubleType)
            .register()
    }

    companion object {
        /** `knn_distance(위도, 경도, 기준 위도, 기준 경도)` — 두 지점 사이의 구면 거리(m). */
        const val KNN_DISTANCE = "knn_distance"
    }
}
