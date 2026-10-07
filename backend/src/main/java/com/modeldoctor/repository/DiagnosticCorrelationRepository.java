package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticCorrelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticCorrelationRepository extends JpaRepository<DiagnosticCorrelation, Long> {
    List<DiagnosticCorrelation> findByRunIdOrderByPriorityScoreDesc(String runId);
    Optional<DiagnosticCorrelation> findByRunIdAndRuleIdAndCorrelationKey(String runId, String ruleId, String correlationKey);
    void deleteByRunId(String runId);
    long countByRunId(String runId);
}
