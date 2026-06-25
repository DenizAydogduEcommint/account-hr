package com.ecommint.accounthr.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ecommint.accounthr.audit.AuditInterceptor;

/**
 * {@link AuditInterceptor}'ı Hibernate'in session-factory interceptor'ı olarak kaydeder
 * (E1-05). Interceptor bir Spring bean DEĞİLDİR (Hibernate tarafından bilinir); ihtiyaç
 * duyduğu {@code AuditFlusher}'a {@code AuditFlusherHolder} statik köprüsü ile erişir.
 *
 * <p>Bu yaklaşım hem PostgreSQL hem H2 (test) altında çalışır; audit yakalama JPA
 * katmanında olduğu için profilden bağımsızdır.
 */
@Configuration
public class HibernateAuditConfig {

    @Bean
    public HibernatePropertiesCustomizer auditInterceptorCustomizer() {
        AuditInterceptor interceptor = new AuditInterceptor();
        return (Map<String, Object> hibernateProperties) ->
                hibernateProperties.put("hibernate.session_factory.interceptor", interceptor);
    }
}
