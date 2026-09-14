package com.suretyseven.docflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suretyseven.docflow.entity.DocumentValidationError;

public interface DocumentValidationErrorRepository extends JpaRepository<DocumentValidationError, Long> {

    List<DocumentValidationError> findByDocumentId(String documentId);
}
