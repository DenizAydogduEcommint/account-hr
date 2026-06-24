package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.ServiceContact;

public interface ServiceContactRepository extends JpaRepository<ServiceContact, Long> {

    List<ServiceContact> findByServiceId(Long serviceId);
}
