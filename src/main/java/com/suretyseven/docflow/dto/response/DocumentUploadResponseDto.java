package com.suretyseven.docflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponseDto {

    private String documentId;
    private String s3Key;
    private String status;
}
