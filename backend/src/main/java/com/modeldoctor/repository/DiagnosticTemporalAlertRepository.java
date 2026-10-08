package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticTemporalAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DiagnosticTemporalAlertRepository extends JpaRepository<DiagnosticTemporalAlert, Long> {

    List<DiagnosticTemporalAlert> findByModelLineageIdOrderByCreatedAtDesc(String modelLineageId);

    List<DiagnosticTemporalAlert> findByModelLineageIdAndAcknowledgedFalseOrderByCreatedAtDesc(String modelLineageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticTemporalAlert a WHERE a.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
