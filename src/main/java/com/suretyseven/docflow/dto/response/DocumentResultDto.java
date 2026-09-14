package com.suretyseven.docflow.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResultDto {

    private String companyName;
    private String registrationNumber;
    private String address;
    private BigDecimal annualRevenue;
    private LocalDate documentDate;
    private LocalDateTime extractedAt;
}
