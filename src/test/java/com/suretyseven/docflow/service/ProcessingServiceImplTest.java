package com.suretyseven.docflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.entity.Document;
import com.suretyseven.docflow.entity.DocumentHistory;
import com.suretyseven.docflow.entity.DocumentValidationError;
import com.suretyseven.docflow.repository.DocumentHistoryRepository;
import com.suretyseven.docflow.repository.DocumentRepository;
import com.suretyseven.docflow.repository.DocumentResultRepository;
import com.suretyseven.docflow.repository.DocumentValidationErrorRepository;
import com.suretyseven.docflow.service.impl.ProcessingServiceImpl;

@ExtendWith(MockitoExtension.class)
class ProcessingServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentHistoryRepository documentHistoryRepository;

    @Mock
    private DocumentResultRepository documentResultRepository;

    @Mock
    private DocumentValidationErrorRepository documentValidationErrorRepository;

    /**
     * Overrides the random mock processor and the retry backoff sleep so processing outcomes
     * are deterministic and tests run instantly.
     */
    private class ScriptedProcessingService extends ProcessingServiceImpl {
        private final Deque<String> scriptedResults = new ArrayDeque<>();

        ScriptedProcessingService(String... results) {
            super(documentRepository, documentHistoryRepository, documentResultRepository, documentValidationErrorRepository);
            for (String result : results) {
                scriptedResults.addLast(result);
            }
        }

        @Override
        protected String mockProcessor(String documentType) {
            return scriptedResults.isEmpty() ? AppConstants.PROCESSOR_RESULT_SUCCESS : scriptedResults.removeFirst();
        }

        @Override
        protected void sleepForBackoff(int retryCount) {
            // no-op: skip real sleeping in tests
        }
    }

    private Document document;

    @BeforeEach
    void setUp() {
        document = Document.builder()
                .id("DOC-33333")
                .filename("invoice.pdf")
                .s3Key("documents/DOC-33333/invoice.pdf")
                .documentType("FINANCIAL_STATEMENT")
                .status(AppConstants.STATUS_UPLOADED)
                .fileHash("etag")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(documentRepository.findById("DOC-33333")).thenReturn(Optional.of(document));
    }

    @Test
    void processDocument_success_marksDocumentProcessed() {
        ScriptedProcessingService service = new ScriptedProcessingService(AppConstants.PROCESSOR_RESULT_SUCCESS);

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_PROCESSED);
        verify(documentResultRepository, times(1)).save(any());
        verify(documentValidationErrorRepository, times(0)).save(any());
    }

    @Test
    void processDocument_successWithBlankCompanyName_marksFailedAndSavesValidationError() {
        ScriptedProcessingService service = new ScriptedProcessingService(AppConstants.PROCESSOR_RESULT_SUCCESS) {
            @Override
            protected com.suretyseven.docflow.dto.response.DocumentResultDto buildMockExtractedData() {
                return com.suretyseven.docflow.dto.response.DocumentResultDto.builder()
                        .companyName("   ")
                        .registrationNumber(AppConstants.MOCK_REGISTRATION_NUMBER)
                        .address(AppConstants.MOCK_ADDRESS)
                        .annualRevenue(new java.math.BigDecimal(AppConstants.MOCK_ANNUAL_REVENUE))
                        .documentDate(java.time.LocalDate.now())
                        .build();
            }
        };

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_FAILED);
        verify(documentResultRepository, times(0)).save(any());

        ArgumentCaptor<DocumentValidationError> errorCaptor = ArgumentCaptor.forClass(DocumentValidationError.class);
        verify(documentValidationErrorRepository, times(1)).save(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getFieldName()).isEqualTo("companyName");

        ArgumentCaptor<DocumentHistory> historyCaptor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(documentHistoryRepository, atLeast(1)).save(historyCaptor.capture());
        boolean hasValidationFailedReason = historyCaptor.getAllValues().stream()
                .anyMatch(h -> AppConstants.REASON_VALIDATION_FAILED.equals(h.getReason()));
        assertThat(hasValidationFailedReason).isTrue();
    }

    @Test
    void processDocument_timeoutThenSuccess_retriesAndEventuallyProcessed() {
        ScriptedProcessingService service = new ScriptedProcessingService(
                AppConstants.PROCESSOR_RESULT_TIMEOUT, AppConstants.PROCESSOR_RESULT_SUCCESS);

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_PROCESSED);
        assertThat(document.getRetryCount()).isEqualTo(1);
        verify(documentResultRepository, times(1)).save(any());

        ArgumentCaptor<DocumentHistory> historyCaptor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(documentHistoryRepository, atLeast(3)).save(historyCaptor.capture());
        boolean hasTimeoutReason = historyCaptor.getAllValues().stream()
                .anyMatch(h -> AppConstants.REASON_TIMEOUT.equals(h.getReason()));
        assertThat(hasTimeoutReason).isTrue();
    }

    @Test
    void processDocument_errorExhaustsRetries_marksFailedWithMaxRetriesReason() {
        ScriptedProcessingService service = new ScriptedProcessingService(
                AppConstants.PROCESSOR_RESULT_ERROR,
                AppConstants.PROCESSOR_RESULT_ERROR,
                AppConstants.PROCESSOR_RESULT_ERROR);

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_FAILED);
        assertThat(document.getRetryCount()).isEqualTo(AppConstants.MAX_RETRIES);

        ArgumentCaptor<DocumentHistory> historyCaptor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(documentHistoryRepository, atLeast(1)).save(historyCaptor.capture());
        boolean hasMaxRetriesReason = historyCaptor.getAllValues().stream()
                .anyMatch(h -> AppConstants.REASON_MAX_RETRIES.equals(h.getReason()));
        assertThat(hasMaxRetriesReason).isTrue();
    }

    @Test
    void processDocument_invalidResult_marksFailedWithoutRetry() {
        ScriptedProcessingService service = new ScriptedProcessingService(AppConstants.PROCESSOR_RESULT_INVALID);

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_FAILED);
        assertThat(document.getRetryCount()).isEqualTo(0);
        verify(documentResultRepository, times(0)).save(any());
    }

    /**
     * Sleep is stubbed out but the real (non-scripted) mockProcessor is exercised here to
     * verify the documentType-based overrides against the live implementation.
     */
    private class RealMockProcessorService extends ProcessingServiceImpl {
        RealMockProcessorService() {
            super(documentRepository, documentHistoryRepository, documentResultRepository, documentValidationErrorRepository);
        }

        @Override
        protected void sleepForBackoff(int retryCount) {
            // no-op: skip real sleeping in tests
        }
    }

    @Test
    void processDocument_documentTypeTestFail_alwaysTimesOutAndRetries() {
        document.setDocumentType(AppConstants.DOCUMENT_TYPE_TEST_FAIL);
        RealMockProcessorService service = new RealMockProcessorService();

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_FAILED);
        assertThat(document.getRetryCount()).isEqualTo(AppConstants.MAX_RETRIES);

        ArgumentCaptor<DocumentHistory> historyCaptor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(documentHistoryRepository, atLeast(1)).save(historyCaptor.capture());
        boolean allTimeoutOrMaxRetries = historyCaptor.getAllValues().stream()
                .filter(h -> h.getReason() != null)
                .allMatch(h -> AppConstants.REASON_TIMEOUT.equals(h.getReason()) || AppConstants.REASON_MAX_RETRIES.equals(h.getReason()));
        assertThat(allTimeoutOrMaxRetries).isTrue();
    }

    @Test
    void processDocument_documentTypeTestInvalid_alwaysFailsWithoutRetry() {
        document.setDocumentType(AppConstants.DOCUMENT_TYPE_TEST_INVALID);
        RealMockProcessorService service = new RealMockProcessorService();

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_FAILED);
        assertThat(document.getRetryCount()).isEqualTo(0);
        verify(documentResultRepository, times(0)).save(any());
    }

    @Test
    void processDocument_documentTypeTestSuccess_alwaysProcessed() {
        document.setDocumentType(AppConstants.DOCUMENT_TYPE_TEST_SUCCESS);
        RealMockProcessorService service = new RealMockProcessorService();

        service.processDocument("DOC-33333");

        assertThat(document.getStatus()).isEqualTo(AppConstants.STATUS_PROCESSED);
        assertThat(document.getRetryCount()).isEqualTo(0);
        verify(documentResultRepository, times(1)).save(any());
    }
}
