package com.kakaobank.coinbox.financialtransaction.repository;

import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자금 이동을 대표하는 금융거래 이벤트를 저장하고 조회한다.
 */
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
}
