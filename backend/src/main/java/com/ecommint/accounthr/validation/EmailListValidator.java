package com.ecommint.accounthr.validation;

import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;

/**
 * {@link EmailList} doğrulayıcısı: değeri virgülle böler ve her token'ı standart
 * Jakarta Bean Validation {@code @Email} kuralıyla denetler.
 *
 * <p>Doğrulama yalnızca <b>public Jakarta Validation API</b>'si üzerinden yapılır
 * (token'lar küçük bir {@link EmailHolder} sarmalayıcısına konup tekil
 * {@code @Email} kuralıyla denetlenir); Hibernate Validator'ın {@code internal}
 * sınıflarına bağımlılık YOKTUR — sürüm yükseltmelerinde kırılmaz.
 *
 * <ul>
 *   <li>{@code null} → geçerli (zorunluluk ayrı {@code @NotBlank} ile ifade edilir).</li>
 *   <li>Her token trim edilir; herhangi biri boş ya da geçersiz e-posta ise → geçersiz.</li>
 *   <li>En az bir geçerli adres bulunmalıdır (tamamen boş string geçersiz).</li>
 * </ul>
 */
public class EmailListValidator implements ConstraintValidator<EmailList, String> {

    /**
     * Tekil {@code @Email} kuralını taşıyan iç sarmalayıcı. Her token bunun bir
     * örneğine konup standart Validator ile denetlenir.
     */
    private static final class EmailHolder {
        @Email
        private final String value;

        private EmailHolder(String value) {
            this.value = value;
        }
    }

    /** Bootstrap maliyeti olmasın diye tek, paylaşılan (thread-safe) Validator. */
    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String[] tokens = value.split(",", -1);
        boolean any = false;
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                // Boş token (ör. "a@x.com,," ya da baştaki/sondaki virgül) geçersiz.
                return false;
            }
            Set<ConstraintViolation<EmailHolder>> violations =
                    VALIDATOR.validate(new EmailHolder(trimmed));
            if (!violations.isEmpty()) {
                return false;
            }
            any = true;
        }
        return any;
    }
}
