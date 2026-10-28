package com.yong.travel.common.persistence

import org.hibernate.boot.model.FunctionContributions
import org.hibernate.boot.model.FunctionContributor
import org.hibernate.type.StandardBasicTypes

/**
 * PostGIS 거리 연산자를 조회 함수로 등록한다.
 * 식이 위치 인덱스 표현식과 같아야 인덱스를 탄다.
 */
class PostgisFunctions : FunctionContributor {

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
        /** `knn_distance(위도, 경도, 기준 위도, 기준 경도)` — 두 지점 사이 거리(m). */
        const val KNN_DISTANCE = "knn_distance"
    }
}
