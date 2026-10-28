package com.compliancevault.vaultapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VaultApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(VaultApiApplication.class, args);
	}

}
