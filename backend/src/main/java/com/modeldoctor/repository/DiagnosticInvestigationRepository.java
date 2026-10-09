package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticInvestigationRepository extends JpaRepository<DiagnosticInvestigation, Long> {

    List<DiagnosticInvestigation> findByRunIdOrderByPriorityScoreDesc(String runId);

    List<DiagnosticInvestigation> findByTargetKeyOrderByPriorityScoreDesc(String targetKey);

    Optional<DiagnosticInvestigation> findByRunIdAndTargetKey(String runId, String targetKey);

    void deleteByRunId(String runId);

    long countByRunId(String runId);
}
