package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Servisleri kullanan takım. */
@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    /**
     * Takım adı. DB tekilliği büyük/küçük-harf DUYARSIZDIR: V16 fonksiyonel index
     * {@code uq_teams_name_lower (lower(name))} ile yönetilir (providers V11 / services V14
     * deseninin aynısı). Bu yüzden {@code unique = true} KALDIRILDI — case-sensitive bir
     * tekil kısıt importer'ın {@code findByNameIgnoreCase} dedup'ı ile tutarsız olurdu.
     * {@code nullable=false} korunur; ddl-auto=validate hâlâ geçer (kolon NOT NULL).
     */
    @Column(name = "name", nullable = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
