package com.kakaobank.coinbox.account.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import com.kakaobank.coinbox.product.entity.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "ACCOUNT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    private static final int ACCOUNT_NUMBER_LENGTH = 14;

    @Id
    @Column(nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Column(nullable = false, unique = true, length = ACCOUNT_NUMBER_LENGTH, updatable = false)
    private String accountNumber;

    @Column(updatable = false)
    private Long parentAccountId;

    @Column(nullable = false)
    private Long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @Column(nullable = false, updatable = false)
    private LocalDate accountOpenDate;

    /**
     * 상품 유형과 개설일을 포함한 계좌를 생성한다. 저금통은 호출 계층에서 최초 잔액을 0원으로 전달한다.
     */
    public static Account create(
            Long accountId,
            Long customerId,
            ProductType productType,
            String accountNumber,
            Long parentAccountId,
            Long balance,
            LocalDate accountOpenDate
    ) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("accountNumber must not be blank");
        }
        if (accountNumber.length() > ACCOUNT_NUMBER_LENGTH) {
            throw new IllegalArgumentException("accountNumber must not exceed " + ACCOUNT_NUMBER_LENGTH + " characters");
        }
        if (balance == null || balance < 0L) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        Account account = new Account();
        account.accountId = accountId;
        account.customerId = customerId;
        account.productType = productType;
        account.accountNumber = accountNumber;
        account.parentAccountId = parentAccountId;
        account.balance = balance;
        account.accountStatus = AccountStatus.ACTIVE;
        account.accountOpenDate = accountOpenDate;
        return account;
    }

    /**
     * 잠금이 획득된 정상 계좌에서만 출금해 동시 거래의 잔액 유실을 방지한다.
     */
    public void debit(Long amount) {
        validateTransferAmount(amount);
        validateActive();
        if (balance < amount) {
            throw new IllegalStateException("Insufficient account balance");
        }
        balance -= amount;
    }

    /**
     * 잠금이 획득된 정상 계좌에 입금하고 오버플로를 검사한다.
     */
    public void credit(Long amount) {
        validateTransferAmount(amount);
        validateActive();
        balance = Math.addExact(balance, amount);
    }

    /**
     * 잔액이 모두 정리된 계좌만 최종 해지 상태로 전환한다.
     */
    public void close() {
        if (balance != 0L) {
            throw new IllegalStateException("Account balance must be zero before closing");
        }
        accountStatus = AccountStatus.CLOSED;
    }

    private void validateTransferAmount(Long amount) {
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private void validateActive() {
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account must be active");
        }
    }
}
