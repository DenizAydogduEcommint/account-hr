package com.ecommint.accounthr.domain.converter;

import com.ecommint.accounthr.service.crypto.EncryptionService;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter}: bir String alanı DB'ye yazarken AES-256-GCM ile
 * şifreler, okurken çözer (E1-05). {@code @Convert} ile işaretlenmiş alanlar (ör.
 * {@code ServiceCredential.secret}) DB'de CIPHERTEXT olarak durur, uygulama tarafında
 * şeffaf biçimde düz metin görünür.
 *
 * <p>{@code autoApply=false}: yalnızca açıkça {@code @Convert(converter=...)} verilen
 * alanlara uygulanır — gelişigüzel tüm String'ler şifrelenmez.
 *
 * <p>JPA converter'ları varsayılan olarak Spring bean DEĞİLDİR; bu yüzden
 * {@link EncryptionService}'i constructor injection ile alamayız. Bunun yerine
 * {@link EncryptionServiceHolder} adlı küçük bir {@code @Component}, servisi statik bir
 * alana yerleştirir; converter onu oradan okur. Holder uygulama başlangıcında set
 * edildiği için, herhangi bir persist/load işleminden önce hazırdır.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return service().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return service().decrypt(dbData);
    }

    private EncryptionService service() {
        EncryptionService service = EncryptionServiceHolder.get();
        if (service == null) {
            throw new IllegalStateException(
                    "EncryptionService henüz hazır değil — EncryptionServiceHolder set edilmedi. "
                            + "Şifreli alanlar Spring context boot olmadan kullanılamaz.");
        }
        return service;
    }
}
