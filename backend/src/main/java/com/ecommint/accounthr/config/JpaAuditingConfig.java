package com.ecommint.accounthr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spring Data JPA auditing'i aktive eder (BaseEntity'deki @CreatedDate /
 * @LastModifiedDate alanlarını otomatik doldurmak için).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
