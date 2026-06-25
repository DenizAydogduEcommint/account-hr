package com.ecommint.accounthr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    /** İsme göre (büyük/küçük harf duyarsız) sağlayıcı bul — importer eşleme. */
    Optional<Provider> findByNameIgnoreCase(String name);
}
