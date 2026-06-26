package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Takımları ada göre alfabetik sıralı döner (picker kaynağı için). */
    List<Team> findAllByOrderByNameAsc();
}
