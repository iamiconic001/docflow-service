package com.suretyseven.docflow.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.constants.ResponseMessages;
import com.suretyseven.docflow.dto.response.DocumentDetailDto;
import com.suretyseven.docflow.dto.response.DocumentHistoryDto;
import com.suretyseven.docflow.dto.response.DocumentListDto;
import com.suretyseven.docflow.dto.response.DocumentResultDto;
import com.suretyseven.docflow.dto.response.DocumentUploadResponseDto;
import com.suretyseven.docflow.dto.response.PresignedUrlResponseDto;
import com.suretyseven.docflow.dto.response.ValidationErrorDto;
import com.suretyseven.docflow.entity.Document;
import com.suretyseven.docflow.entity.DocumentHistory;
import com.suretyseven.docflow.exception.DocumentNotFoundException;
import com.suretyseven.docflow.exception.DuplicateDocumentException;
import com.suretyseven.docflow.repository.DocumentHistoryRepository;
import com.suretyseven.docflow.repository.DocumentRepository;
import com.suretyseven.docflow.repository.DocumentResultRepository;
import com.suretyseven.docflow.repository.DocumentValidationErrorRepository;
import com.suretyseven.docflow.service.DocumentService;
import com.suretyseven.docflow.service.ProcessingService;
import com.suretyseven.docflow.service.S3Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentResultRepository documentResultRepository;
    private final DocumentValidationErrorRepository documentValidationErrorRepository;
    private final DocumentHistoryRepository documentHistoryRepository;
    private final S3Service s3Service;
    private final S3Client s3Client;
    private final ProcessingService processingService;
    private final String bucketName;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final SecureRandom secureRandom = new SecureRandom();

    public DocumentServiceImpl(DocumentRepository documentRepository,
                                DocumentResultRepository documentResultRepository,
                                DocumentValidationErrorRepository documentValidationErrorRepository,
                                DocumentHistoryRepository documentHistoryRepository,
                                S3Service s3Service,
                                S3Client s3Client,
                                ProcessingService processingService,
                                @Value("${aws.s3.bucket-name}") String bucketName) {
        this.documentRepository = documentRepository;
        this.documentResultRepository = documentResultRepository;
        this.documentValidationErrorRepository = documentValidationErrorRepository;
        this.documentHistoryRepository = documentHistoryRepository;
        this.s3Service = s3Service;
        this.s3Client = s3Client;
        this.processingService = processingService;
        this.bucketName = bucketName;
    }

    @Override
    public DocumentUploadResponseDto uploadDocument(MultipartFile file, String documentType, String metadata) {
        byte[] fileBytes = readBytes(file);

        String documentId = generateUniqueDocumentId();
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String s3Key = AppConstants.S3_FOLDER + documentId + "/" + originalFilename;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        String presignedPutUrl = s3Service.generatePresignedPutUrl(s3Key, contentType);
        uploadToPresignedUrl(presignedPutUrl, fileBytes, contentType);

        String eTag = fetchETag(s3Key);

        Optional<Document> existingDocument = documentRepository.findByFileHash(eTag);
        if (existingDocument.isPresent()) {
            Document existing = existingDocument.get();
            log.info("Duplicate document detected: documentId={}", existing.getId());
            s3Service.deleteObject(s3Key);
            throw new DuplicateDocumentException(ResponseMessages.DUPLICATE_DOCUMENT,
                    existing.getId(), existing.getS3Key(), existing.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        Document document = Document.builder()
                .id(documentId)
                .filename(originalFilename)
                .s3Key(s3Key)
                .documentType(documentType)
                .status(AppConstants.STATUS_UPLOADED)
                .metadata(metadata)
                .fileHash(eTag)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        documentRepository.save(document);

        saveHistory(document, AppConstants.STATUS_UPLOADED, null, null);
        log.info("Document uploaded: documentId={}, filename={}, documentType={}", documentId, originalFilename, documentType);

        processingService.processDocument(documentId);

        return DocumentUploadResponseDto.builder()
                .documentId(documentId)
                .s3Key(s3Key)
                .status(AppConstants.STATUS_UPLOADED)
                .build();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded file", ex);
        }
    }

    protected void uploadToPresignedUrl(String presignedUrl, byte[] fileBytes, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(presignedUrl))
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(ResponseMessages.UPLOAD_FAILED);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(ResponseMessages.UPLOAD_FAILED, ex);
        }
    }

    private String fetchETag(String s3Key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);
        String eTag = headObjectResponse.eTag();
        return eTag != null ? eTag.replace("\"", "") : null;
    }

    private String generateUniqueDocumentId() {
        int bound = (int) Math.pow(10, AppConstants.DOC_ID_RANDOM_DIGITS);
        for (int attempt = 0; attempt < AppConstants.MAX_DOC_ID_GENERATION_ATTEMPTS; attempt++) {
            int randomNumber = secureRandom.nextInt(bound);
            String candidate = AppConstants.DOC_ID_PREFIX
                    + String.format("%0" + AppConstants.DOC_ID_RANDOM_DIGITS + "d", randomNumber);
            if (!documentRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique document id");
    }

    @Override
    public DocumentDetailDto getDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(ResponseMessages.DOCUMENT_NOT_FOUND));

        DocumentResultDto resultDto = documentResultRepository.findByDocumentId(documentId)
                .map(result -> DocumentResultDto.builder()
                        .companyName(result.getCompanyName())
                        .registrationNumber(result.getRegistrationNumber())
                        .address(result.getAddress())
                        .annualRevenue(result.getAnnualRevenue())
                        .documentDate(result.getDocumentDate())
                        .extractedAt(result.getExtractedAt())
                        .build())
                .orElse(null);

        List<ValidationErrorDto> validationErrors = documentValidationErrorRepository.findByDocumentId(documentId)
                .stream()
                .map(error -> ValidationErrorDto.builder()
                        .fieldName(error.getFieldName())
                        .errorMessage(error.getErrorMessage())
                        .build())
                .toList();

        return DocumentDetailDto.builder()
                .documentId(document.getId())
                .filename(document.getFilename())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .metadata(document.getMetadata())
                .retryCount(document.getRetryCount())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .result(resultDto)
                .validationErrors(validationErrors.isEmpty() ? null : validationErrors)
                .build();
    }

    @Override
    public List<DocumentHistoryDto> getDocumentHistory(String documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new DocumentNotFoundException(ResponseMessages.DOCUMENT_NOT_FOUND);
        }

        return documentHistoryRepository.findByDocumentIdOrderByCreatedAtAsc(documentId)
                .stream()
                .map(history -> DocumentHistoryDto.builder()
                        .status(history.getStatus())
                        .reason(history.getReason())
                        .attemptNumber(history.getAttemptNumber())
                        .createdAt(history.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public PresignedUrlResponseDto getDownloadUrl(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(ResponseMessages.DOCUMENT_NOT_FOUND));

        String presignedUrl = s3Service.generatePresignedGetUrl(document.getS3Key());
        return PresignedUrlResponseDto.builder().presignedUrl(presignedUrl).build();
    }

    @Override
    public Page<DocumentListDto> listDocuments(String status, String documentType, Pageable pageable) {
        Page<Document> page;
        boolean hasStatus = StringUtils.hasText(status);
        boolean hasType = StringUtils.hasText(documentType);

        if (hasStatus && hasType) {
            page = documentRepository.findByStatusAndDocumentTypeOrderByCreatedAtDesc(status, documentType, pageable);
        } else if (hasStatus) {
            page = documentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (hasType) {
            page = documentRepository.findByDocumentTypeOrderByCreatedAtDesc(documentType, pageable);
        } else {
            page = documentRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return page.map(document -> DocumentListDto.builder()
                .documentId(document.getId())
                .filename(document.getFilename())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .build());
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
