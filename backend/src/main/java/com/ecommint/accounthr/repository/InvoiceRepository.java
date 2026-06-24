package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByExpenseId(Long expenseId);
}
