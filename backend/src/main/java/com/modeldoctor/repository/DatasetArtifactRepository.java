package com.modeldoctor.repository;

import com.modeldoctor.domain.DatasetArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetArtifactRepository extends JpaRepository<DatasetArtifactEntity, String> {
    Optional<DatasetArtifactEntity> findBySha256(String sha256);
    List<DatasetArtifactEntity> findByIsDeletedFalseOrderByCreatedAtDesc();
    Optional<DatasetArtifactEntity> findByIdAndIsDeletedFalse(String id);
}
