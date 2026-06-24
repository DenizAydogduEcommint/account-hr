package com.ecommint.accounthr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByLastFour(String lastFour);
}
