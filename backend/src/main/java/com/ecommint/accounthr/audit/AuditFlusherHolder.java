package com.ecommint.accounthr.audit;

import org.springframework.stereotype.Component;

/**
 * {@link AuditInterceptor}'a (Spring bean OLMAYAN Hibernate interceptor) {@link AuditFlusher}'ı
 * ulaştıran köprü (E1-05). Hibernate interceptor'ı bizim oluşturduğumuz bir örnektir ve
 * transaction synchronization içinde flusher'a ihtiyaç duyar; bu holder Spring tarafından
 * set edilir.
 */
@Component
public class AuditFlusherHolder {

    private static volatile AuditFlusher instance;

    public AuditFlusherHolder(AuditFlusher auditFlusher) {
        instance = auditFlusher;
    }

    public static AuditFlusher get() {
        return instance;
    }
}
