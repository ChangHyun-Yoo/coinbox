package com.kakaobank.coinbox.financialtransaction.repository;

import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionStatus;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("금융 거래 Repository")
class FinancialTransactionRepositoryTest {

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Test
    @DisplayName("계좌 이체 거래를 성공 상태로 저장한다")
    void savesSuccessfulTransferTransaction() {
        // given: 성공 상태로 생성되는 계좌 이체 거래를 준비한다.
        Long transactionId = 1L;
        FinancialTransaction transaction = FinancialTransaction.create(
                transactionId,
                TransactionType.TRANSFER,
                10_000L,
                LocalDateTime.of(2026, 8, 23, 12, 0)
        );
        financialTransactionRepository.saveAndFlush(transaction);

        // when: 거래 ID로 금융 거래를 조회한다.
        Optional<FinancialTransaction> foundTransaction = financialTransactionRepository.findById(transactionId);

        // then: 계좌 이체 거래가 성공 상태로 저장되어 있다.
        assertThat(foundTransaction)
                .isPresent()
                .get()
                .returns(TransactionType.TRANSFER, FinancialTransaction::getTransactionType)
                .returns(TransactionStatus.SUCCESS, FinancialTransaction::getTransactionStatus);
    }
}
