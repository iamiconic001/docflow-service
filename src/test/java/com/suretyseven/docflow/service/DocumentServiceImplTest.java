package com.suretyseven.docflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.dto.response.DocumentDetailDto;
import com.suretyseven.docflow.dto.response.DocumentUploadResponseDto;
import com.suretyseven.docflow.entity.Document;
import com.suretyseven.docflow.exception.DocumentNotFoundException;
import com.suretyseven.docflow.exception.DuplicateDocumentException;
import com.suretyseven.docflow.repository.DocumentHistoryRepository;
import com.suretyseven.docflow.repository.DocumentRepository;
import com.suretyseven.docflow.repository.DocumentResultRepository;
import com.suretyseven.docflow.repository.DocumentValidationErrorRepository;
import com.suretyseven.docflow.service.impl.DocumentServiceImpl;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentResultRepository documentResultRepository;

    @Mock
    private DocumentValidationErrorRepository documentValidationErrorRepository;

    @Mock
    private DocumentHistoryRepository documentHistoryRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private S3Client s3Client;

    @Mock
    private ProcessingService processingService;

    /**
     * Skips the real HTTP PUT against a presigned URL so uploads can be tested without
     * network access; every other code path runs unmodified.
     */
    private class TestableDocumentService extends DocumentServiceImpl {
        TestableDocumentService() {
            super(documentRepository, documentResultRepository, documentValidationErrorRepository,
                    documentHistoryRepository, s3Service, s3Client, processingService, "test-bucket");
        }

        @Override
        protected void uploadToPresignedUrl(String presignedUrl, byte[] fileBytes, String contentType) {
            // no-op: real S3 PUT is out of scope for a unit test
        }
    }

    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        documentService = new TestableDocumentService();
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "invoice.pdf", "application/pdf", "hello-world".getBytes());
    }

    @Test
    void uploadDocument_validFile_returnsUploadedStatus() {
        when(documentRepository.existsById(anyString())).thenReturn(false);
        when(s3Service.generatePresignedPutUrl(anyString(), anyString())).thenReturn("https://s3.example.com/put");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().eTag("\"abc123\"").build());
        when(documentRepository.findByFileHash("abc123")).thenReturn(Optional.empty());

        DocumentUploadResponseDto response = documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null);

        assertThat(response.getStatus()).isEqualTo(AppConstants.STATUS_UPLOADED);
        assertThat(response.getDocumentId()).startsWith("DOC-");
        assertThat(response.getS3Key()).contains(response.getDocumentId());

        verify(documentRepository, times(1)).save(any(Document.class));
        verify(documentHistoryRepository, times(1)).save(any());
        verify(processingService, times(1)).processDocument(response.getDocumentId());
    }

    @Test
    void uploadDocument_duplicateFileHash_throwsDuplicateDocumentExceptionWithExistingDocumentId() {
        Document existing = Document.builder()
                .id("DOC-11111")
                .filename("invoice.pdf")
                .s3Key("documents/DOC-11111/invoice.pdf")
                .documentType("FINANCIAL_STATEMENT")
                .status(AppConstants.STATUS_UPLOADED)
                .fileHash("abc123")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.existsById(anyString())).thenReturn(false);
        when(s3Service.generatePresignedPutUrl(anyString(), anyString())).thenReturn("https://s3.example.com/put");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().eTag("\"abc123\"").build());
        when(documentRepository.findByFileHash("abc123")).thenReturn(Optional.of(existing));
        doNothing().when(s3Service).deleteObject(anyString());

        assertThatThrownBy(() -> documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null))
                .isInstanceOf(DuplicateDocumentException.class)
                .satisfies(ex -> {
                    DuplicateDocumentException duplicate = (DuplicateDocumentException) ex;
                    assertThat(duplicate.getExistingDocumentId()).isEqualTo("DOC-11111");
                    assertThat(duplicate.getMessage()).isEqualTo(com.suretyseven.docflow.constants.ResponseMessages.DUPLICATE_DOCUMENT);
                });

        verify(documentRepository, never()).save(any(Document.class));
        verify(processingService, never()).processDocument(anyString());
    }

    @Test
    void getDocument_notFound_throwsDocumentNotFoundException() {
        when(documentRepository.findById("DOC-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument("DOC-99999"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getDocument_found_returnsFullDetail() {
        Document document = Document.builder()
                .id("DOC-22222")
                .filename("report.pdf")
                .s3Key("documents/DOC-22222/report.pdf")
                .documentType("INSURANCE_POLICY")
                .status(AppConstants.STATUS_PROCESSED)
                .metadata("{}")
                .fileHash("etag123")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findById("DOC-22222")).thenReturn(Optional.of(document));
        when(documentResultRepository.findByDocumentId("DOC-22222")).thenReturn(Optional.empty());
        when(documentValidationErrorRepository.findByDocumentId("DOC-22222")).thenReturn(Collections.emptyList());

        DocumentDetailDto detail = documentService.getDocument("DOC-22222");

        assertThat(detail.getDocumentId()).isEqualTo("DOC-22222");
        assertThat(detail.getStatus()).isEqualTo(AppConstants.STATUS_PROCESSED);
        assertThat(detail.getFilename()).isEqualTo("report.pdf");
    }
}
