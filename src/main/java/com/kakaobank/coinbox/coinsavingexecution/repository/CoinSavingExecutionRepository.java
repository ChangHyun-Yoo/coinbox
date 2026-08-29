package com.kakaobank.coinbox.coinsavingexecution.repository;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

/**
 * 저금통별 일별 동전모으기 실행 이력을 저장하고 중복 실행을 확인한다.
 */
public interface CoinSavingExecutionRepository extends JpaRepository<CoinSavingExecution, Long> {

    /**
     * 같은 저금통과 실행일 조합의 처리 완료 여부를 확인한다.
     */
    boolean existsByCoinBoxIdAndExecutionDate(Long coinBoxId, LocalDate executionDate);
}
