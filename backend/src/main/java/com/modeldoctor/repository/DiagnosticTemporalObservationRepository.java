package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticTemporalObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DiagnosticTemporalObservationRepository extends JpaRepository<DiagnosticTemporalObservation, Long> {

    List<DiagnosticTemporalObservation> findByModelLineageIdOrderByTimestampAsc(String modelLineageId);

    List<DiagnosticTemporalObservation> findByModelLineageIdAndMetricNameAndTargetKeyOrderByTimestampAsc(
            String modelLineageId, String metricName, String targetKey);

    List<DiagnosticTemporalObservation> findByRunId(String runId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticTemporalObservation o WHERE o.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
