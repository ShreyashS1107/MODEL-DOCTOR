package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticIncident;
import com.modeldoctor.domain.IncidentLifecycleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticIncidentRepository extends JpaRepository<DiagnosticIncident, Long> {

    List<DiagnosticIncident> findByModelLineageIdOrderByPriorityScoreDesc(String modelLineageId);

    List<DiagnosticIncident> findByModelLineageIdAndLifecycleStateOrderByPriorityScoreDesc(String modelLineageId, IncidentLifecycleState lifecycleState);

    List<DiagnosticIncident> findByModelLineageIdAndLifecycleStateNotOrderByPriorityScoreDesc(String modelLineageId, IncidentLifecycleState lifecycleState);

    Optional<DiagnosticIncident> findByModelLineageIdAndIncidentFingerprint(String modelLineageId, String incidentFingerprint);

    long countByModelLineageIdAndLifecycleStateNot(String modelLineageId, IncidentLifecycleState lifecycleState);

    void deleteByModelLineageId(String modelLineageId);
}
