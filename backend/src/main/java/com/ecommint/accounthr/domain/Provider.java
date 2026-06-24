package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Fatura kesen firma (AWS, Anthropic, Contabo...). */
@Entity
@Table(name = "providers")
public class Provider extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
