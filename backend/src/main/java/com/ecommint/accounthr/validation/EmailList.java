package com.ecommint.accounthr.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Virgülle ayrılmış bir veya birden çok e-posta adresini doğrular (E3-02 / E6).
 *
 * <p>Tek bir adres veya {@code "a@x.com, b@y.com"} biçiminde çoklu adres geçerlidir;
 * her adres tek tek {@code @Email} kuralıyla denetlenir. {@code null} geçerli sayılır
 * (zorunluluk {@code @NotBlank} ile ayrıca ifade edilir). Boş/whitespace token'lar
 * (ör. baştaki/sondaki virgül) geçersizdir.
 */
@Documented
@Constraint(validatedBy = EmailListValidator.class)
@Target({ FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface EmailList {

    String message() default "must be a valid email address (comma-separated allowed)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
