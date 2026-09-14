package com.yong.travel.place.repository

import com.yong.travel.place.domain.Place
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface PlaceRepository : JpaRepository<Place, Long>, JpaSpecificationExecutor<Place>
