package com.suretyseven.docflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.constants.ResponseMessages;
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

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    private static final byte[] SAMPLE_BYTES = "hello-world".getBytes();

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
    private ProcessingService processingService;

    /**
     * Skips the real HTTP PUT against a presigned URL so uploads can be tested without
     * network access; every other code path runs unmodified.
     */
    private class TestableDocumentService extends DocumentServiceImpl {
        TestableDocumentService() {
            super(documentRepository, documentResultRepository, documentValidationErrorRepository,
                    documentHistoryRepository, s3Service, processingService);
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
        return new MockMultipartFile("file", "invoice.pdf", "application/pdf", SAMPLE_BYTES);
    }

    private String sampleFileHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(SAMPLE_BYTES);
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void uploadDocument_validFile_returnsUploadedStatus() {
        when(documentRepository.findByFileHash(sampleFileHash())).thenReturn(Collections.emptyList());
        when(documentRepository.existsById(anyString())).thenReturn(false);
        when(s3Service.generatePresignedPutUrl(anyString(), anyString())).thenReturn("https://s3.example.com/put");

        DocumentUploadResponseDto response = documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null);

        assertThat(response.getStatus()).isEqualTo(AppConstants.STATUS_UPLOADED);
        assertThat(response.getDocumentId()).startsWith("DOC-");
        assertThat(response.getS3Key()).contains(response.getDocumentId());

        verify(documentRepository, times(1)).save(any(Document.class));
        verify(documentHistoryRepository, times(1)).save(any());
        verify(processingService, times(1)).processDocument(response.getDocumentId());
    }

    @Test
    void uploadDocument_duplicateOfProcessedDocument_throwsDuplicateDocumentExceptionAndSkipsS3() {
        Document existing = Document.builder()
                .id("DOC-11111")
                .filename("invoice.pdf")
                .s3Key("documents/DOC-11111/invoice.pdf")
                .documentType("FINANCIAL_STATEMENT")
                .status(AppConstants.STATUS_PROCESSED)
                .fileHash(sampleFileHash())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByFileHash(sampleFileHash())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null))
                .isInstanceOf(DuplicateDocumentException.class)
                .satisfies(ex -> {
                    DuplicateDocumentException duplicate = (DuplicateDocumentException) ex;
                    assertThat(duplicate.getExistingDocumentId()).isEqualTo("DOC-11111");
                    assertThat(duplicate.getMessage()).isEqualTo(ResponseMessages.DUPLICATE_DOCUMENT);
                });

        verify(s3Service, never()).generatePresignedPutUrl(anyString(), anyString());
        verify(s3Service, never()).deleteObject(anyString());
        verify(documentRepository, never()).save(any(Document.class));
        verify(processingService, never()).processDocument(anyString());
    }

    @Test
    void uploadDocument_duplicateOfUploadedOrProcessingDocument_throwsDuplicateDocumentException() {
        Document existing = Document.builder()
                .id("DOC-55555")
                .filename("invoice.pdf")
                .s3Key("documents/DOC-55555/invoice.pdf")
                .documentType("FINANCIAL_STATEMENT")
                .status(AppConstants.STATUS_PROCESSING)
                .fileHash(sampleFileHash())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByFileHash(sampleFileHash())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null))
                .isInstanceOf(DuplicateDocumentException.class);

        verify(s3Service, never()).generatePresignedPutUrl(anyString(), anyString());
        verify(documentRepository, never()).save(any(Document.class));
        verify(processingService, never()).processDocument(anyString());
    }

    @Test
    void uploadDocument_duplicateOfFailedDocument_createsNewDocumentReusingS3KeyWithoutReupload() {
        Document existing = Document.builder()
                .id("DOC-44444")
                .filename("invoice.pdf")
                .s3Key("documents/DOC-44444/invoice.pdf")
                .documentType("FINANCIAL_STATEMENT")
                .status(AppConstants.STATUS_FAILED)
                .fileHash(sampleFileHash())
                .retryCount(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByFileHash(sampleFileHash())).thenReturn(List.of(existing));
        when(documentRepository.existsById(anyString())).thenReturn(false);

        DocumentUploadResponseDto response = documentService.uploadDocument(sampleFile(), "FINANCIAL_STATEMENT", null);

        assertThat(response.getStatus()).isEqualTo(AppConstants.STATUS_UPLOADED);
        assertThat(response.getDocumentId()).isNotEqualTo("DOC-44444");
        assertThat(response.getS3Key()).isEqualTo(existing.getS3Key());

        // No new upload: the presigned PUT flow must never be invoked for a failed-document retry.
        verify(s3Service, never()).generatePresignedPutUrl(anyString(), anyString());
        verify(s3Service, never()).deleteObject(anyString());

        // The old failed record itself is never touched/updated.
        verify(documentRepository, never()).save(existing);

        verify(documentRepository, times(1)).save(any(Document.class));
        verify(processingService, times(1)).processDocument(response.getDocumentId());
    }

    @Test
    void uploadDocument_multipleFailedDocumentsShareSameHash_retriesFromMostRecentWithoutError() {
        // Reproduces: same PDF uploaded twice before, both attempts ended FAILED, so two rows
        // share the same fileHash. A third upload must not blow up on a non-unique lookup.
        Document olderFailed = Document.builder()
                .id("DOC-34659")
                .filename("file-example_PDF_500_kB.pdf")
                .s3Key("documents/DOC-34659/file-example_PDF_500_kB.pdf")
                .documentType("TEST_FAIL")
                .status(AppConstants.STATUS_FAILED)
                .fileHash(sampleFileHash())
                .retryCount(3)
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .updatedAt(LocalDateTime.now().minusMinutes(30))
                .build();
        Document newerFailed = Document.builder()
                .id("DOC-66053")
                .filename("file-example_PDF_500_kB.pdf")
                .s3Key("documents/DOC-66053/file-example_PDF_500_kB.pdf")
                .documentType("TEST_INVALID")
                .status(AppConstants.STATUS_FAILED)
                .fileHash(sampleFileHash())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByFileHash(sampleFileHash())).thenReturn(List.of(olderFailed, newerFailed));
        when(documentRepository.existsById(anyString())).thenReturn(false);

        DocumentUploadResponseDto response = documentService.uploadDocument(sampleFile(), "TEST_SUCCESS", null);

        assertThat(response.getStatus()).isEqualTo(AppConstants.STATUS_UPLOADED);
        assertThat(response.getS3Key()).isEqualTo(newerFailed.getS3Key());
        verify(processingService, times(1)).processDocument(response.getDocumentId());
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
