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
public class DocumentHistoryDto {

    private String status;
    private String reason;
    private Integer attemptNumber;
    private LocalDateTime createdAt;
}
