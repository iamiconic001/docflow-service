package com.suretyseven.docflow.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.constants.ResponseMessages;
import com.suretyseven.docflow.dto.response.DocumentResultDto;
import com.suretyseven.docflow.dto.response.ValidationErrorDto;
import com.suretyseven.docflow.entity.Document;
import com.suretyseven.docflow.entity.DocumentHistory;
import com.suretyseven.docflow.entity.DocumentResult;
import com.suretyseven.docflow.entity.DocumentValidationError;
import com.suretyseven.docflow.repository.DocumentHistoryRepository;
import com.suretyseven.docflow.repository.DocumentRepository;
import com.suretyseven.docflow.repository.DocumentResultRepository;
import com.suretyseven.docflow.repository.DocumentValidationErrorRepository;
import com.suretyseven.docflow.service.ProcessingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProcessingServiceImpl implements ProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentHistoryRepository documentHistoryRepository;
    private final DocumentResultRepository documentResultRepository;
    private final DocumentValidationErrorRepository documentValidationErrorRepository;

    public ProcessingServiceImpl(DocumentRepository documentRepository,
                                  DocumentHistoryRepository documentHistoryRepository,
                                  DocumentResultRepository documentResultRepository,
                                  DocumentValidationErrorRepository documentValidationErrorRepository) {
        this.documentRepository = documentRepository;
        this.documentHistoryRepository = documentHistoryRepository;
        this.documentResultRepository = documentResultRepository;
        this.documentValidationErrorRepository = documentValidationErrorRepository;
    }

    @Override
    @Async("docflowAsyncExecutor")
    public void processDocument(String documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document not found for processing: documentId={}", documentId);
            return;
        }

        int attemptNumber = document.getRetryCount() + 1;

        document.setStatus(AppConstants.STATUS_PROCESSING);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        saveHistory(document, AppConstants.STATUS_PROCESSING, null, attemptNumber);
        log.info("Processing started: documentId={}, attemptNumber={}", documentId, attemptNumber);

        String mockResult = mockProcessor(document.getDocumentType());
        log.info("Mock processor result: documentId={}, attemptNumber={}, result={}", documentId, attemptNumber, mockResult);

        switch (mockResult) {
            case AppConstants.PROCESSOR_RESULT_TIMEOUT -> handleRetryable(document, attemptNumber, AppConstants.REASON_TIMEOUT);
            case AppConstants.PROCESSOR_RESULT_ERROR -> handleRetryable(document, attemptNumber, AppConstants.REASON_ERROR);
            case AppConstants.PROCESSOR_RESULT_INVALID -> handleInvalidResult(document, attemptNumber);
            case AppConstants.PROCESSOR_RESULT_SUCCESS -> handleSuccess(document, attemptNumber);
            default -> log.error("Unknown mock processor result: documentId={}, result={}", documentId, mockResult);
        }
    }

    protected String mockProcessor(String documentType) {
        if (AppConstants.DOCUMENT_TYPE_TEST_FAIL.equals(documentType)) {
            return AppConstants.PROCESSOR_RESULT_TIMEOUT;
        }
        if (AppConstants.DOCUMENT_TYPE_TEST_INVALID.equals(documentType)) {
            return AppConstants.PROCESSOR_RESULT_INVALID;
        }
        if (AppConstants.DOCUMENT_TYPE_TEST_SUCCESS.equals(documentType)) {
            return AppConstants.PROCESSOR_RESULT_SUCCESS;
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60) {
            return AppConstants.PROCESSOR_RESULT_SUCCESS;
        } else if (roll < 75) {
            return AppConstants.PROCESSOR_RESULT_TIMEOUT;
        } else if (roll < 90) {
            return AppConstants.PROCESSOR_RESULT_ERROR;
        } else {
            return AppConstants.PROCESSOR_RESULT_INVALID;
        }
    }

    private void handleRetryable(Document document, int attemptNumber, String reason) {
        String documentId = document.getId();
        document.setRetryCount(document.getRetryCount() + 1);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        saveHistory(document, AppConstants.STATUS_FAILED, reason, attemptNumber);
        log.warn("Retry candidate: documentId={}, attemptNumber={}, reason={}", documentId, attemptNumber, reason);

        if (document.getRetryCount() < AppConstants.MAX_RETRIES) {
            log.warn("Retry triggered: documentId={}, attemptNumber={}", documentId, attemptNumber);
            sleepForBackoff(document.getRetryCount());
            processDocument(documentId);
        } else {
            document.setStatus(AppConstants.STATUS_FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
            saveHistory(document, AppConstants.STATUS_FAILED, AppConstants.REASON_MAX_RETRIES, attemptNumber);
            log.error("Max retries exceeded: documentId={}", documentId);
        }
    }

    private void handleInvalidResult(Document document, int attemptNumber) {
        String documentId = document.getId();
        document.setStatus(AppConstants.STATUS_FAILED);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        saveHistory(document, AppConstants.STATUS_FAILED, AppConstants.REASON_INVALID, attemptNumber);
        log.error("Processing failed: documentId={}, reason={}, attemptNumber={}", documentId, AppConstants.REASON_INVALID, attemptNumber);
    }

    private void handleSuccess(Document document, int attemptNumber) {
        String documentId = document.getId();
        DocumentResultDto extractedData = buildMockExtractedData();
        List<ValidationErrorDto> validationErrors = validate(extractedData);

        if (!validationErrors.isEmpty()) {
            List<String> failedFields = validationErrors.stream().map(ValidationErrorDto::getFieldName).toList();
            log.warn("Validation errors: documentId={}, fields={}", documentId, failedFields);

            for (ValidationErrorDto error : validationErrors) {
                documentValidationErrorRepository.save(DocumentValidationError.builder()
                        .document(document)
                        .fieldName(error.getFieldName())
                        .errorMessage(error.getErrorMessage())
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            document.setStatus(AppConstants.STATUS_FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
            saveHistory(document, AppConstants.STATUS_FAILED, AppConstants.REASON_VALIDATION_FAILED, attemptNumber);
            log.error("Processing failed: documentId={}, reason={}, attemptNumber={}", documentId, AppConstants.REASON_VALIDATION_FAILED, attemptNumber);
            return;
        }

        documentResultRepository.save(DocumentResult.builder()
                .document(document)
                .companyName(extractedData.getCompanyName())
                .registrationNumber(extractedData.getRegistrationNumber())
                .address(extractedData.getAddress())
                .annualRevenue(extractedData.getAnnualRevenue())
                .documentDate(extractedData.getDocumentDate())
                .extractedAt(LocalDateTime.now())
                .build());

        document.setStatus(AppConstants.STATUS_PROCESSED);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        saveHistory(document, AppConstants.STATUS_PROCESSED, null, attemptNumber);
        log.info("Processing success: documentId={}", documentId);
    }

    private List<ValidationErrorDto> validate(DocumentResultDto extractedData) {
        List<ValidationErrorDto> errors = new ArrayList<>();

        if (extractedData.getCompanyName() == null || extractedData.getCompanyName().isBlank()) {
            errors.add(ValidationErrorDto.builder()
                    .fieldName("companyName")
                    .errorMessage("companyName" + ResponseMessages.FIELD_REQUIRED_SUFFIX)
                    .build());
        }

        if (extractedData.getRegistrationNumber() == null || extractedData.getRegistrationNumber().isBlank()) {
            errors.add(ValidationErrorDto.builder()
                    .fieldName("registrationNumber")
                    .errorMessage("registrationNumber" + ResponseMessages.FIELD_REQUIRED_SUFFIX)
                    .build());
        }

        if (extractedData.getAnnualRevenue() == null || extractedData.getAnnualRevenue().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(ValidationErrorDto.builder()
                    .fieldName("annualRevenue")
                    .errorMessage(ResponseMessages.ANNUAL_REVENUE_INVALID)
                    .build());
        }

        if (extractedData.getDocumentDate() == null || extractedData.getDocumentDate().isAfter(LocalDate.now())) {
            errors.add(ValidationErrorDto.builder()
                    .fieldName("documentDate")
                    .errorMessage(ResponseMessages.DOCUMENT_DATE_INVALID)
                    .build());
        }

        return errors;
    }

    protected void sleepForBackoff(int retryCount) {
        try {
            long backoffMillis = 1000L * (1L << retryCount);
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    protected DocumentResultDto buildMockExtractedData() {
        return DocumentResultDto.builder()
                .companyName(AppConstants.MOCK_COMPANY_NAME)
                .registrationNumber(AppConstants.MOCK_REGISTRATION_NUMBER)
                .address(AppConstants.MOCK_ADDRESS)
                .annualRevenue(new BigDecimal(AppConstants.MOCK_ANNUAL_REVENUE))
                .documentDate(LocalDate.now())
                .build();
    }

    private void saveHistory(Document document, String status, String reason, Integer attemptNumber) {
        DocumentHistory history = DocumentHistory.builder()
                .document(document)
                .status(status)
                .reason(reason)
                .attemptNumber(attemptNumber)
                .createdAt(LocalDateTime.now())
                .build();
        documentHistoryRepository.save(history);
    }
}
