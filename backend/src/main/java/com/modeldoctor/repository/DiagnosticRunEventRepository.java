package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticRunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticRunEventRepository extends JpaRepository<DiagnosticRunEvent, Long> {
    List<DiagnosticRunEvent> findByRunIdOrderByTimestampAsc(String runId);
    void deleteByRunId(String runId);
}
