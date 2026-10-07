package com.modeldoctor.repository;

import com.modeldoctor.domain.ModelArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelArtifactRepository extends JpaRepository<ModelArtifactEntity, String> {
    Optional<ModelArtifactEntity> findBySha256(String sha256);
    List<ModelArtifactEntity> findByIsDeletedFalseOrderByCreatedAtDesc();
    Optional<ModelArtifactEntity> findByIdAndIsDeletedFalse(String id);
}
