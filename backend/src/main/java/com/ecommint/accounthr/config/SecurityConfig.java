package com.ecommint.accounthr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security skeleton. There is no authentication yet (E1-01 is just the skeleton),
 * so everything is permitted. CSRF is disabled and sessions are stateless because
 * this is a REST API that will use JWT bearer tokens.
 *
 * TODO: JWT auth in E1-xx — replace permitAll() with stateless JWT validation,
 *       protect /api/v1/** and keep /api/health + /actuator/health public.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> {}) // use CorsConfigurationSource bean (CorsConfig)
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/health").permitAll()
				.requestMatchers("/actuator/**").permitAll()
				.anyRequest().permitAll() // TODO: JWT auth in E1-xx — lock this down
			)
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());
		return http.build();
	}
}
