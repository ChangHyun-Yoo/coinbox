package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.accountdailybalance.entity.AccountDailyBalance;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 일별 계좌 잔액 스냅샷의 기본 저장과 조회를 담당한다.
 */
public interface AccountDailyBalanceRepository extends JpaRepository<AccountDailyBalance, Long> {
}
