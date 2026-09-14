package com.suretyseven.docflow.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentListDto {

    private String documentId;
    private String filename;
    private String documentType;
    private String status;
    private LocalDateTime createdAt;
}
