package com.compliancevault.vaultapi.service;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class DocumentRedactionService {

    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final TextPiiRedactor redactor;
    private final String rawBucket;
    private final String cleanBucket;
    private final String auditBucket;

    public DocumentRedactionService(
            S3Client s3Client,
            ObjectMapper objectMapper,
            TextPiiRedactor redactor,
            @Value("${app.storage.s3.bucket-raw}") String rawBucket,
            @Value("${app.storage.s3.bucket-clean}") String cleanBucket,
            @Value("${app.storage.s3.bucket-audit}") String auditBucket) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
        this.rawBucket = rawBucket;
        this.cleanBucket = cleanBucket;
        this.auditBucket = auditBucket;
    }

    public void redactAndStore(
            UUID eventId,
            UUID documentId,
            String rawObjectKey,
            String contentType) throws Exception {

        String normalizedType = contentType == null ? "" : contentType.split(";", 2)[0].trim();

        if (!normalizedType.equalsIgnoreCase("text/plain")
                && !normalizedType.equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException(
                    "Unsupported content type for redaction: " + normalizedType);
        }

        byte[] rawBytes = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(rawBucket)
                        .key(rawObjectKey)
                        .build())
                .asByteArray();

        if (rawBytes.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException(
                    "Document exceeds the 10 MiB processing limit");
        }

        String originalText = new String(rawBytes, StandardCharsets.UTF_8);

        TextPiiRedactor.RedactionResult result;
        byte[] cleanBytes;

        if (normalizedType.equalsIgnoreCase("application/json")) {
            Object parsedJson = objectMapper.readValue(originalText, Object.class);
            RedactionCounts counts = new RedactionCounts();

            Object redactedJson = redactJsonValue(parsedJson, counts);

            cleanBytes = objectMapper.writeValueAsBytes(redactedJson);

            result = new TextPiiRedactor.RedactionResult(
                    new String(cleanBytes, StandardCharsets.UTF_8),
                    counts.aadhaar,
                    counts.pan,
                    counts.email,
                    counts.phone);
        } else {
            result = redactor.redact(originalText);
            cleanBytes = result.text().getBytes(StandardCharsets.UTF_8);
        }

        String cleanObjectKey = documentId + "/redacted-" + rawObjectKey;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(cleanBucket)
                        .key(cleanObjectKey)
                        .contentType(normalizedType)
                        .build(),
                RequestBody.fromBytes(cleanBytes));

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("receiptVersion", 1);
        receipt.put("eventId", eventId.toString());
        receipt.put("documentId", documentId.toString());
        receipt.put("rawObjectKey", rawObjectKey);
        receipt.put("cleanObjectKey", cleanObjectKey);
        receipt.put("contentType", normalizedType);
        receipt.put("rawSha256", sha256(rawBytes));
        receipt.put("cleanSha256", sha256(cleanBytes));
        receipt.put("redactionCounts", Map.of(
                "aadhaar", result.aadhaarCount(),
                "pan", result.panCount(),
                "email", result.emailCount(),
                "phone", result.phoneCount()));
        receipt.put("processedAt", Instant.now().toString());
        receipt.put("workerPod", System.getenv().getOrDefault("HOSTNAME", "unknown"));
        receipt.put("workerNode", System.getenv().getOrDefault("NODE_NAME", "unknown"));

        byte[] receiptBytes = objectMapper.writeValueAsBytes(receipt);
        String receiptKey = documentId + "/" + eventId + ".json";

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(auditBucket)
                        .key(receiptKey)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromBytes(receiptBytes));
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return HexFormat.of().formatHex(digest);
    }

    private Object redactJsonValue(Object value, RedactionCounts counts) {
        if (value instanceof String text) {
            TextPiiRedactor.RedactionResult result = redactor.redact(text);
            counts.aadhaar += result.aadhaarCount();
            counts.pan += result.panCount();
            counts.email += result.emailCount();
            counts.phone += result.phoneCount();
            return result.text();
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                redacted.put(
                        (String) entry.getKey(),
                        redactJsonValue(entry.getValue(), counts));
            }

            return redacted;
        }

        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());

            for (Object item : list) {
                redacted.add(redactJsonValue(item, counts));
            }

            return redacted;
        }

        return value;
    }

    private static class RedactionCounts {
        int aadhaar;
        int pan;
        int email;
        int phone;
    }
}