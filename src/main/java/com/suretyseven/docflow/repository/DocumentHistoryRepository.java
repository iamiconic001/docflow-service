package com.suretyseven.docflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suretyseven.docflow.entity.DocumentHistory;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistory, Long> {

    List<DocumentHistory> findByDocumentIdOrderByCreatedAtAsc(String documentId);
}
