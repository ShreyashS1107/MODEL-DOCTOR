package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.DiagnosticResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticResultRepository extends JpaRepository<DiagnosticResult, Long> {
    List<DiagnosticResult> findByRunId(String runId);
    List<DiagnosticResult> findByRunIdOrderByIdAsc(String runId);
    Optional<DiagnosticResult> findByRunIdAndModule(String runId, DiagnosticModule module);
    void deleteByRunId(String runId);
}
