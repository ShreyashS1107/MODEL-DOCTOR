package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticReliabilityEvent;
import com.modeldoctor.domain.ReliabilityEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticReliabilityEventRepository extends JpaRepository<DiagnosticReliabilityEvent, Long> {

    List<DiagnosticReliabilityEvent> findByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    List<DiagnosticReliabilityEvent> findTop50ByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    List<DiagnosticReliabilityEvent> findByModelLineageIdAndEventTypeOrderByTimestampDesc(String modelLineageId, ReliabilityEventType eventType);

    Optional<DiagnosticReliabilityEvent> findByModelLineageIdAndEventTypeAndSourceTypeAndSourceId(
            String modelLineageId, ReliabilityEventType eventType, String sourceType, String sourceId);

    void deleteByModelLineageId(String modelLineageId);
}
