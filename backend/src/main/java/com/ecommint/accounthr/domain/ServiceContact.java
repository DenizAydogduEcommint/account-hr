package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Bir servisin fatura iletişim bilgisi (mail, panel login referansı, kaynak). */
@Entity
@Table(name = "service_contacts")
public class ServiceContact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "email")
    private String email;

    @Column(name = "panel_login_ref")
    private String panelLoginRef;

    @Column(name = "source")
    private String source;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPanelLoginRef() {
        return panelLoginRef;
    }

    public void setPanelLoginRef(String panelLoginRef) {
        this.panelLoginRef = panelLoginRef;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
