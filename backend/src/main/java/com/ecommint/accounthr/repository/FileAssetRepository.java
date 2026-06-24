package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.FileAsset;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    List<FileAsset> findByInvoiceId(Long invoiceId);
}
