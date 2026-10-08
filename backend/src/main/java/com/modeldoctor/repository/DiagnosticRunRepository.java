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
    List<DiagnosticRun> findByModelNameAndRunTypeOrderByCreatedAtAsc(String modelName, String runType);
    List<DiagnosticRun> findByModelNameOrderByCreatedAtDesc(String modelName);
}
