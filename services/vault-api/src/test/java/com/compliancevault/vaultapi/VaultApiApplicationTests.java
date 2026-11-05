
package com.compliancevault.vaultapi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class VaultApiApplicationTests {

    @BeforeAll
    static void setDemoCredentials() {
        if (System.getenv("VAULT_DEMO_USERNAME") == null
                || System.getenv("VAULT_DEMO_PASSWORD") == null) {
            throw new IllegalStateException(
                "Test process needs VAULT_DEMO_USERNAME and VAULT_DEMO_PASSWORD"
            );
        }
    }

    @Autowired
    private S3Client s3Client;

    @Test
    void s3ClientBeanLoads() {
        assertNotNull(s3Client);
    }
}
