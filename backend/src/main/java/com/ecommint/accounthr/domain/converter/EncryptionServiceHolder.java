package com.ecommint.accounthr.domain.converter;

import org.springframework.stereotype.Component;

import com.ecommint.accounthr.service.crypto.EncryptionService;

/**
 * {@link EncryptedStringConverter}'a {@link EncryptionService}'i ulaştıran köprü (E1-05).
 *
 * <p>JPA converter'ları Spring tarafından yönetilmediği için DI ile servis enjekte
 * edilemez. Bu {@code @Component}, Spring tarafından oluşturulur ve servisi statik bir
 * alana koyar; converter çalışma anında {@link #get()} ile ona erişir.
 */
@Component
public class EncryptionServiceHolder {

    private static volatile EncryptionService instance;

    public EncryptionServiceHolder(EncryptionService encryptionService) {
        instance = encryptionService;
    }

    /** Converter'ın kullandığı erişim noktası. Context boot olmadan {@code null} olabilir. */
    public static EncryptionService get() {
        return instance;
    }
}
