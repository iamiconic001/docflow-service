package com.suretyseven.docflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suretyseven.docflow.entity.DocumentResult;

public interface DocumentResultRepository extends JpaRepository<DocumentResult, Long> {

    Optional<DocumentResult> findByDocumentId(String documentId);
}
