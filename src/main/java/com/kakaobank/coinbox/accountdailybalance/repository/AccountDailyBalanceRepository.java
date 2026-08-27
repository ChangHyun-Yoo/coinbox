package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.accountdailybalance.entity.AccountDailyBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDailyBalanceRepository extends JpaRepository<AccountDailyBalance, Long> {
}
