package com.kakaobank.coinbox.financialtransaction.repository;

import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
}
