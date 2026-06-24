package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Servisleri kullanan takım. */
@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
