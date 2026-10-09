package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticIncidentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticIncidentEventRepository extends JpaRepository<DiagnosticIncidentEvent, Long> {

    List<DiagnosticIncidentEvent> findByIncidentIdOrderByTimestampDesc(Long incidentId);

    List<DiagnosticIncidentEvent> findByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    void deleteByModelLineageId(String modelLineageId);
}
