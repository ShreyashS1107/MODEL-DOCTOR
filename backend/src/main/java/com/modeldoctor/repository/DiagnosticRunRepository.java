package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.domain.DiagnosticStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticRunRepository extends JpaRepository<DiagnosticRun, String> {
    List<DiagnosticRun> findByStatus(DiagnosticStatus status);
    List<DiagnosticRun> findAllByOrderByCreatedAtDesc();
    List<DiagnosticRun> findByModelNameOrderByCreatedAtAsc(String modelName);
    List<DiagnosticRun> findByModelArtifactIdOrderByCreatedAtAsc(String modelArtifactId);
    List<DiagnosticRun> findByModelNameAndRunTypeOrderByCreatedAtAsc(String modelName, String runType);
    List<DiagnosticRun> findByModelNameOrderByCreatedAtDesc(String modelName);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.modelName FROM DiagnosticRun r WHERE r.modelName IS NOT NULL")
    List<String> findDistinctModelLineageIds();
}
