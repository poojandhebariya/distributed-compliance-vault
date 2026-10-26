package com.compliancevault.vaultapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VaultController {
    @GetMapping("/api/v1/vault/status")
    public Map<String, String> getStatus() {
        return Map.of(
                "service", "vault-api",
                "status", "running"
        );
    }
}
