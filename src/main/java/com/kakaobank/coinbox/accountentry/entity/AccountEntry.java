package com.kakaobank.coinbox.accountentry.entity;

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
@Table(name = "ACCOUNT_ENTRY")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntry extends BaseEntity {

    private static final int ENTRY_DESCRIPTION_LENGTH = 50;

    @Id
    @Column(nullable = false, updatable = false)
    private Long entryId;

    @Column(nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryCode entryCode;

    @Column(nullable = false, updatable = false)
    private Long amount;

    @Column(nullable = false, updatable = false)
    private Long balanceBefore;

    @Column(nullable = false, updatable = false)
    private Long balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDatetime;

    @Column(nullable = false, length = ENTRY_DESCRIPTION_LENGTH, updatable = false)
    private String entryDescription;

    /**
     * 특정 계좌에서 실제 출금된 내역을 생성하고 거래 후 잔액을 계산한다.
     */
    public static AccountEntry withdrawal(
            Long entryId,
            Long accountId,
            Long transactionId,
            EntryCode entryCode,
            Long amount,
            Long balanceBefore,
            LocalDateTime transactionDatetime,
            String entryDescription
    ) {
        validateCommonFields(amount, transactionDatetime, entryDescription);
        if (balanceBefore == null || balanceBefore < amount) {
            throw new IllegalArgumentException("balanceBefore must be greater than or equal to amount");
        }
        AccountEntry accountEntry = new AccountEntry();
        accountEntry.entryId = entryId;
        accountEntry.accountId = accountId;
        accountEntry.transactionId = transactionId;
        accountEntry.entryType = EntryType.WITHDRAWAL;
        accountEntry.entryCode = entryCode;
        accountEntry.amount = amount;
        accountEntry.balanceBefore = balanceBefore;
        accountEntry.balanceAfter = balanceBefore - amount;
        accountEntry.transactionDatetime = transactionDatetime;
        accountEntry.entryDescription = entryDescription;
        return accountEntry;
    }

    /**
     * 특정 계좌에 실제 입금된 내역을 생성하고 거래 후 잔액을 계산한다.
     */
    public static AccountEntry deposit(
            Long entryId,
            Long accountId,
            Long transactionId,
            EntryCode entryCode,
            Long amount,
            Long balanceBefore,
            LocalDateTime transactionDatetime,
            String entryDescription
    ) {
        validateCommonFields(amount, transactionDatetime, entryDescription);
        if (balanceBefore == null || balanceBefore < 0L) {
            throw new IllegalArgumentException("balanceBefore must not be negative");
        }
        AccountEntry accountEntry = new AccountEntry();
        accountEntry.entryId = entryId;
        accountEntry.accountId = accountId;
        accountEntry.transactionId = transactionId;
        accountEntry.entryType = EntryType.DEPOSIT;
        accountEntry.entryCode = entryCode;
        accountEntry.amount = amount;
        accountEntry.balanceBefore = balanceBefore;
        accountEntry.balanceAfter = Math.addExact(balanceBefore, amount);
        accountEntry.transactionDatetime = transactionDatetime;
        accountEntry.entryDescription = entryDescription;
        return accountEntry;
    }

    private static void validateCommonFields(
            Long amount,
            LocalDateTime transactionDatetime,
            String entryDescription
    ) {
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (transactionDatetime == null) {
            throw new IllegalArgumentException("transactionDatetime must not be null");
        }
        if (entryDescription == null || entryDescription.isBlank()
                || entryDescription.length() > ENTRY_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("entryDescription must be between 1 and 50 characters");
        }
    }
}
