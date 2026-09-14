package com.suretyseven.docflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.suretyseven.docflow.dto.response.DocumentDetailDto;
import com.suretyseven.docflow.dto.response.DocumentHistoryDto;
import com.suretyseven.docflow.dto.response.DocumentListDto;
import com.suretyseven.docflow.dto.response.DocumentUploadResponseDto;
import com.suretyseven.docflow.dto.response.PresignedUrlResponseDto;

import java.util.List;

public interface DocumentService {

    DocumentUploadResponseDto uploadDocument(MultipartFile file, String documentType, String metadata);

    DocumentDetailDto getDocument(String documentId);

    List<DocumentHistoryDto> getDocumentHistory(String documentId);

    PresignedUrlResponseDto getDownloadUrl(String documentId);

    Page<DocumentListDto> listDocuments(String status, String documentType, Pageable pageable);
}
