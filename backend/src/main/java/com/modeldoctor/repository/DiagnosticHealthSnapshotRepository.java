package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticHealthSnapshotRepository extends JpaRepository<DiagnosticHealthSnapshot, Long> {

    List<DiagnosticHealthSnapshot> findByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    Optional<DiagnosticHealthSnapshot> findTopByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    Optional<DiagnosticHealthSnapshot> findByModelLineageIdAndRunId(String modelLineageId, String runId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticHealthSnapshot s WHERE s.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
