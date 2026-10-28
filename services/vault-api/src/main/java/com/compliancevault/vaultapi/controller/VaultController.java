
package com.compliancevault.vaultapi.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import com.compliancevault.vaultapi.service.DocumentPersistenceService;
import com.compliancevault.vaultapi.storage.S3StorageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VaultController {

    private final S3StorageService storageService;
    private final DocumentPersistenceService documentPersistenceService;

    public VaultController(
            S3StorageService storageService,
            DocumentPersistenceService documentPersistenceService) {
        this.storageService = storageService;
        this.documentPersistenceService = documentPersistenceService;
    }

    @GetMapping("/api/v1/vault/status")
    public Map<String, String> getStatus() {
        return Map.of(
                "service", "vault-api",
                "status", "running"
        );
    }

    @PostMapping(
            value = "/api/v1/vault/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Uploaded file must not be empty"
            );
        }

        try {
            // Step 1: Store the original file in SeaweedFS raw-zone.
            String objectKey = storageService.store(file);

            // Step 2: Persist metadata and create the outbox event.
            UUID documentId =
                    documentPersistenceService.recordUploadedDocument(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            objectKey
                    );

            // Step 3: Return the successful upload response.
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "status", "uploaded",
                            "documentId", documentId.toString(),
                            "objectKey", objectKey
                    )
            );

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store uploaded document",
                    exception
            );
        }
    }
}
