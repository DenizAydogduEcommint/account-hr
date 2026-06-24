package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Kredi kartı (Akbank Axess ****3800, YKB ****3909, Ziraat ****9164). */
@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @Column(name = "bank", nullable = false)
    private String bank;

    @Column(name = "last_four", nullable = false, unique = true)
    private String lastFour;

    @Column(name = "holder_name")
    private String holderName;

    @Column(name = "label")
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public String getLastFour() {
        return lastFour;
    }

    public void setLastFour(String lastFour) {
        this.lastFour = lastFour;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
