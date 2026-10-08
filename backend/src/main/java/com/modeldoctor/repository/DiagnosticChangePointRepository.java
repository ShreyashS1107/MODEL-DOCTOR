package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticChangePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DiagnosticChangePointRepository extends JpaRepository<DiagnosticChangePoint, Long> {

    List<DiagnosticChangePoint> findByModelLineageIdOrderByChangeTimestampDesc(String modelLineageId);

    List<DiagnosticChangePoint> findByModelLineageIdAndMetricNameAndTargetKeyOrderByChangeTimestampDesc(
            String modelLineageId, String metricName, String targetKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticChangePoint c WHERE c.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
