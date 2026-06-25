package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.FileAsset;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    List<FileAsset> findByInvoiceId(Long invoiceId);

    /** Duplicate tespiti (bütünlük): aynı içerik hash'ine sahip kayıtlar. */
    List<FileAsset> findBySha256(String sha256);

    /**
     * Duplicate tespiti (mantıksal): aynı (provider, invoice_no) ikilisine bağlı
     * mevcut dosyalar. invoice → provider ve invoice_no üzerinden gider.
     */
    List<FileAsset> findByInvoiceProviderIdAndInvoiceInvoiceNo(Long providerId, String invoiceNo);
}
