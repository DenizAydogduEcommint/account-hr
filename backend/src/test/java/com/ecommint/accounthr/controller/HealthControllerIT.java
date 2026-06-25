package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end wiring test: boots the FULL application context (including Spring
 * Security and JPA) against in-memory H2 on a random port, then hits the real
 * HTTP endpoint. Proves the skeleton is wired correctly without any external
 * infrastructure (no PostgreSQL, no Docker).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerIT {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@SuppressWarnings("rawtypes")
	void healthEndpointReturnsUp() {
		ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/health", Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("status")).isEqualTo("UP");
	}
}
