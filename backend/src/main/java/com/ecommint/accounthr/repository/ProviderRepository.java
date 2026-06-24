package com.ecommint.accounthr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
}
