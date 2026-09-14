package com.suretyseven.docflow.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDetailDto {

    private String documentId;
    private String filename;
    private String documentType;
    private String status;
    private String metadata;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private DocumentResultDto result;
    private List<ValidationErrorDto> validationErrors;
}
