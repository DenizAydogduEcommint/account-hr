package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.ServiceCredential;

public interface ServiceCredentialRepository extends JpaRepository<ServiceCredential, Long> {

    List<ServiceCredential> findByServiceId(Long serviceId);
}
