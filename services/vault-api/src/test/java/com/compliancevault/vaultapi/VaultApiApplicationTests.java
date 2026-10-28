
package com.compliancevault.vaultapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class VaultApiApplicationTests {

	@Autowired
	private S3Client s3Client;

	@Test
	void s3ClientBeanLoads() {
		assertNotNull(s3Client);
	}
}
