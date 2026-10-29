package com.compliancevault.vaultapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class DocumentRedactionServiceTest {

    @Test
    void redactsJsonValuesAndPreservesValidJsonStructure() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        ObjectMapper objectMapper = new ObjectMapper();
        TextPiiRedactor redactor = new TextPiiRedactor();

        String originalJson = """
                {
                  "email": "test@example.com",
                  "nested": {
                    "phone": "9876543210",
                    "pan": "ABCDE1234F"
                  },
                  "count": 7,
                  "items": ["first@example.org", "ordinary text"]
                }
                """;

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        originalJson.getBytes(StandardCharsets.UTF_8)));

        DocumentRedactionService service = new DocumentRedactionService(
                s3Client,
                objectMapper,
                redactor,
                "raw-zone",
                "clean-zone",
                "audit-zone");

        service.redactAndStore(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "input/document.json",
                "application/json");

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client, times(2))
                .putObject(requestCaptor.capture(), bodyCaptor.capture());

        List<PutObjectRequest> requests = requestCaptor.getAllValues();
        List<RequestBody> bodies = bodyCaptor.getAllValues();

        int cleanIndex = -1;
        int auditIndex = -1;

        for (int i = 0; i < requests.size(); i++) {
            if ("clean-zone".equals(requests.get(i).bucket())) {
                cleanIndex = i;
            }
            if ("audit-zone".equals(requests.get(i).bucket())) {
                auditIndex = i;
            }
        }

        assertTrue(cleanIndex >= 0, "Clean JSON should be stored");
        assertTrue(auditIndex >= 0, "Audit receipt should be stored");

        String cleanJson;
        try (var stream = bodies.get(cleanIndex).contentStreamProvider().newStream()) {
            cleanJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Parsing proves the redacted output is still valid JSON.
        var cleanTree = objectMapper.readTree(cleanJson);

        assertEquals("[REDACTED_EMAIL]", cleanTree.get("email").asString());
        assertEquals(
                "[REDACTED_PHONE]",
                cleanTree.get("nested").get("phone").asString());
        assertEquals(
                "[REDACTED_PAN]",
                cleanTree.get("nested").get("pan").asString());
        assertEquals(7, cleanTree.get("count").asInt());
        assertEquals(
                "[REDACTED_EMAIL]",
                cleanTree.get("items").get(0).asString());
        assertEquals("ordinary text", cleanTree.get("items").get(1).asString());

        String auditJson;
        try (var stream = bodies.get(auditIndex).contentStreamProvider().newStream()) {
            auditJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var receipt = objectMapper.readTree(auditJson);
        var counts = receipt.get("redactionCounts");

        assertEquals(0, counts.get("aadhaar").asInt());
        assertEquals(1, counts.get("pan").asInt());
        assertEquals(2, counts.get("email").asInt());
        assertEquals(1, counts.get("phone").asInt());
    }
}