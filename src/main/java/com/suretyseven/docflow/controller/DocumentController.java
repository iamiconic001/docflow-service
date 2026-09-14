package com.suretyseven.docflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.suretyseven.docflow.common.ApiResponse;
import com.suretyseven.docflow.constants.ResponseMessages;
import com.suretyseven.docflow.dto.response.DocumentDetailDto;
import com.suretyseven.docflow.dto.response.DocumentHistoryDto;
import com.suretyseven.docflow.dto.response.DocumentListDto;
import com.suretyseven.docflow.dto.response.DocumentUploadResponseDto;
import com.suretyseven.docflow.dto.response.PresignedUrlResponseDto;
import com.suretyseven.docflow.service.DocumentService;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<DocumentUploadResponseDto>> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart("documentType") String documentType,
            @RequestPart(value = "metadata", required = false) String metadata) {
        DocumentUploadResponseDto response = documentService.uploadDocument(file, documentType, metadata);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.DOCUMENT_UPLOADED, response));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentDetailDto>> getDocument(@PathVariable String documentId) {
        DocumentDetailDto response = documentService.getDocument(documentId);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.DOCUMENT_FETCHED, response));
    }

    @GetMapping("/{documentId}/history")
    public ResponseEntity<ApiResponse<List<DocumentHistoryDto>>> getDocumentHistory(@PathVariable String documentId) {
        List<DocumentHistoryDto> response = documentService.getDocumentHistory(documentId);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.HISTORY_FETCHED, response));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<ApiResponse<PresignedUrlResponseDto>> downloadDocument(@PathVariable String documentId) {
        PresignedUrlResponseDto response = documentService.getDownloadUrl(documentId);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.DOWNLOAD_URL_GENERATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DocumentListDto>>> listDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DocumentListDto> response = documentService.listDocuments(status, documentType, pageable);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.DOCUMENT_LIST_FETCHED, response));
    }
}
