package com.ecommint.accounthr.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight, dependency-free health endpoint used by the frontend dashboard
 * and as a smoke test for end-to-end wiring. Distinct from Spring Actuator's
 * /actuator/health (which also checks the DB).
 */
@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}
