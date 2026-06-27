package com.ecommint.accounthr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.enums.UserRole;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    /**
     * Belirli roldeki AKTİF kullanıcı sayısı. Son-aktif-yönetici korumasında
     * (E1-08) "değişiklikten sonra en az bir aktif ADMIN kalmalı" kuralını
     * hesaplamak için kullanılır.
     */
    long countByRoleAndActiveTrue(UserRole role);
}
