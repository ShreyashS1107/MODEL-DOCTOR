package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticAlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DiagnosticAlertEventRepository extends JpaRepository<DiagnosticAlertEvent, Long> {

    List<DiagnosticAlertEvent> findByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    List<DiagnosticAlertEvent> findByAlertIdOrderByTimestampAsc(Long alertId);

    List<DiagnosticAlertEvent> findTop50ByModelLineageIdOrderByTimestampDesc(String modelLineageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticAlertEvent e WHERE e.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
