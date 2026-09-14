package com.suretyseven.docflow.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.suretyseven.docflow.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByFileHash(String fileHash);

    Page<Document> findByStatusAndDocumentTypeOrderByCreatedAtDesc(String status, String documentType, Pageable pageable);

    Page<Document> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Document> findByDocumentTypeOrderByCreatedAtDesc(String documentType, Pageable pageable);

    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
