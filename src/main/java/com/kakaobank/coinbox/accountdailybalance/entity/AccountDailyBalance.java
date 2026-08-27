package com.kakaobank.coinbox.accountdailybalance.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "ACCOUNT_DAILY_BALANCE",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_daily_balance_account_date",
                columnNames = {"account_id", "balance_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDailyBalance extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long accountDailyBalanceId;

    @Column(nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false)
    private LocalDate balanceDate;

    @Column(nullable = false, updatable = false)
    private Long closingBalance;

    public static AccountDailyBalance create(
            Long accountDailyBalanceId,
            Long accountId,
            LocalDate balanceDate,
            Long closingBalance
    ) {
        if (closingBalance == null || closingBalance < 0L) {
            throw new IllegalArgumentException("closingBalance must not be negative");
        }
        AccountDailyBalance accountDailyBalance = new AccountDailyBalance();
        accountDailyBalance.accountDailyBalanceId = accountDailyBalanceId;
        accountDailyBalance.accountId = accountId;
        accountDailyBalance.balanceDate = balanceDate;
        accountDailyBalance.closingBalance = closingBalance;
        return accountDailyBalance;
    }
}
