package com.ecommint.accounthr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Ay/dönem (2026-01, 2026-02 ...). */
@Entity
@Table(name = "periods", uniqueConstraints = {
        // period_year / period_month: "year" ve "month" SQL'de rezerve kelimedir
        // (H2 strict + Postgres), bu yüzden fiziksel kolonlar prefiksli.
        @UniqueConstraint(name = "uq_periods_year_month", columnNames = {"period_year", "period_month"})
})
public class Period extends BaseEntity {

    @Column(name = "period_year", nullable = false)
    private int year;

    @Column(name = "period_month", nullable = false)
    private int month;

    /** Okunabilir kod, ör. "2026-01". */
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
