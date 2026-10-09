package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticRemediation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticRemediationRepository extends JpaRepository<DiagnosticRemediation, Long> {

    List<DiagnosticRemediation> findByRunIdOrderByPriorityScoreDesc(String runId);

    List<DiagnosticRemediation> findByTargetKeyOrderByPriorityScoreDesc(String targetKey);

    List<DiagnosticRemediation> findByRunIdIn(List<String> runIds);

    Optional<DiagnosticRemediation> findByRunIdAndId(String runId, Long id);

    void deleteByRunId(String runId);

    long countByRunId(String runId);
}
