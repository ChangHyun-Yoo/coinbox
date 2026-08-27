package com.kakaobank.coinbox.coinsavingexecution.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "COIN_SAVING_EXECUTION",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coin_saving_execution_coinbox_date",
                        columnNames = {"coinbox_id", "execution_date"}
                ),
                @UniqueConstraint(
                        name = "uk_coin_saving_execution_transaction",
                        columnNames = "transaction_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinSavingExecution extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long executionId;

    @Column(name = "coinbox_id", nullable = false, updatable = false)
    private Long coinBoxId;

    @Column(updatable = false)
    private Long transactionId;

    @Column(nullable = false, updatable = false)
    private LocalDate executionDate;

    @Column(nullable = false, updatable = false)
    private Long savingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CoinSavingExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private CoinSavingReasonCode reasonCode;

    public static CoinSavingExecution success(
            Long executionId,
            Long coinBoxId,
            Long transactionId,
            LocalDate executionDate,
            Long savingAmount
    ) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Successful execution requires transactionId");
        }
        if (savingAmount == null || savingAmount <= 0L) {
            throw new IllegalArgumentException("Successful execution requires a positive savingAmount");
        }

        CoinSavingExecution execution = new CoinSavingExecution();
        execution.executionId = executionId;
        execution.coinBoxId = coinBoxId;
        execution.transactionId = transactionId;
        execution.executionDate = executionDate;
        execution.savingAmount = savingAmount;
        execution.executionStatus = CoinSavingExecutionStatus.SUCCESS;
        return execution;
    }

    public static CoinSavingExecution skipped(
            Long executionId,
            Long coinBoxId,
            LocalDate executionDate,
            CoinSavingReasonCode reasonCode
    ) {
        if (reasonCode == null || reasonCode == CoinSavingReasonCode.SYSTEM_ERROR) {
            throw new IllegalArgumentException("Skipped execution requires a business reasonCode");
        }
        CoinSavingExecution execution = new CoinSavingExecution();
        execution.executionId = executionId;
        execution.coinBoxId = coinBoxId;
        execution.executionDate = executionDate;
        execution.savingAmount = 0L;
        execution.executionStatus = CoinSavingExecutionStatus.SKIPPED;
        execution.reasonCode = reasonCode;
        return execution;
    }

    public static CoinSavingExecution failed(
            Long executionId,
            Long coinBoxId,
            LocalDate executionDate,
            CoinSavingReasonCode reasonCode
    ) {
        if (reasonCode != CoinSavingReasonCode.SYSTEM_ERROR) {
            throw new IllegalArgumentException("Failed execution requires SYSTEM_ERROR reasonCode");
        }
        CoinSavingExecution execution = new CoinSavingExecution();
        execution.executionId = executionId;
        execution.coinBoxId = coinBoxId;
        execution.executionDate = executionDate;
        execution.savingAmount = 0L;
        execution.executionStatus = CoinSavingExecutionStatus.FAILED;
        execution.reasonCode = reasonCode;
        return execution;
    }
}
