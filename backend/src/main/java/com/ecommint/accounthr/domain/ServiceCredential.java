package com.ecommint.accounthr.domain;

import com.ecommint.accounthr.domain.converter.EncryptedStringConverter;
import com.ecommint.accounthr.domain.enums.CredentialType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Bir servise ait hassas erişim bilgisi (E1-05). İleride otomatik fatura indirme (E5)
 * için panel login / API key saklamanın <b>altyapısı</b>; gerçek parolalar burada DEĞİL,
 * E5'te girilecektir. Bir servisin birden çok credential'ı olabilir (service → 1:N).
 *
 * <p>{@code secret} alanı {@link EncryptedStringConverter} ile DB'ye AES-256-GCM
 * şifreli yazılır / okurken çözülür; DB'de ASLA düz metin durmaz. {@code username}
 * düz saklanır (hassas değil). Tablo adı {@code service_credentials}.
 */
@Entity
@Table(name = "service_credentials")
public class ServiceCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "cred_type", nullable = false)
    private CredentialType credType;

    /** Panel/servis kullanıcı adı (hassas değil, düz saklanır). */
    @Column(name = "username")
    private String username;

    /**
     * Parola / API key — DB'de ŞİFRELİ (AES-256-GCM) saklanır. Uygulama tarafında
     * düz metin görünür ama at-rest ciphertext'tir. Loglara ASLA düşmez.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret", columnDefinition = "TEXT")
    private String secret;

    /** İnsan-okur etiket (ör. "Contabo panel - prod"). */
    @Column(name = "label")
    private String label;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public CredentialType getCredType() {
        return credType;
    }

    public void setCredType(CredentialType credType) {
        this.credType = credType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
