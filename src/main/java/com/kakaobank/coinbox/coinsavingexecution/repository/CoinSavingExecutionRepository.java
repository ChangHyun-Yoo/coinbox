package com.kakaobank.coinbox.coinsavingexecution.repository;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinSavingExecutionRepository extends JpaRepository<CoinSavingExecution, Long> {

    boolean existsByCoinBoxIdAndExecutionDate(Long coinBoxId, java.time.LocalDate executionDate);
}
