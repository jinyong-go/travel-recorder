package com.yong.travel.record.repository

import com.yong.travel.record.domain.VisitRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface VisitRecordRepository : JpaRepository<VisitRecord, Long>, JpaSpecificationExecutor<VisitRecord>
