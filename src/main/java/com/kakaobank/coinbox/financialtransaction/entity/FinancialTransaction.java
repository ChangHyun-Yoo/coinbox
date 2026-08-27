package com.kakaobank.coinbox.financialtransaction.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "FINANCIAL_TRANSACTION")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTransaction extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType transactionType;

    @Column(nullable = false, updatable = false)
    private Long transactionAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus transactionStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime executionDatetime;

    /**
     * 자금이 이동한 업무 원인과 전체 금액을 금융 이벤트로 생성한다.
     * 실제 출금·입금 계좌와 방향은 같은 거래 ID를 가진 AccountEntry에서 관리한다.
     */
    public static FinancialTransaction create(
            Long transactionId,
            TransactionType transactionType,
            Long transactionAmount,
            LocalDateTime executionDatetime
    ) {
        if (transactionAmount == null || transactionAmount <= 0L) {
            throw new IllegalArgumentException("transactionAmount must be positive");
        }
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.transactionId = transactionId;
        transaction.transactionType = transactionType;
        transaction.transactionAmount = transactionAmount;
        transaction.transactionStatus = TransactionStatus.SUCCESS;
        transaction.executionDatetime = executionDatetime;
        return transaction;
    }
}
