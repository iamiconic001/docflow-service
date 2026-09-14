package com.suretyseven.docflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    private String id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false)
    private String documentType;

    @Column(nullable = false)
    private String status;

    @Lob
    private String metadata;

    private String fileHash;

    @Builder.Default
    private int retryCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
