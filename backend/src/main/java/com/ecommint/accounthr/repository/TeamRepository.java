package com.ecommint.accounthr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
