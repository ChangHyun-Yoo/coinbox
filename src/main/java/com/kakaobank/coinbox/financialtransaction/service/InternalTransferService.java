package com.kakaobank.coinbox.financialtransaction.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionType;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 같은 은행의 두 계좌를 잠그고 잔액, 금융거래와 계좌 원장을 하나의 트랜잭션으로 반영한다.
 * 상품별 가입·계약 규칙은 호출 서비스의 책임이며 여기서는 공통 계좌 거래 조건만 검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalTransferService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountEntryRepository accountEntryRepository;
    private final Snowflake snowflake;

    /**
     * 지정 금액을 이체한다. 잠금 이후 잔액을 기준으로 출금 가능 여부와 원장 잔액을 계산한다.
     */
    @Transactional
    public TransferResult transfer(
            Long sourceAccountId,
            Long targetAccountId,
            Long amount,
            TransferLedgerSpec ledgerSpec
    ) {
        validateAmount(amount);
        LockedAccounts lockedAccounts = lockAndValidateAccounts(sourceAccountId, targetAccountId);
        validateSufficientBalance(lockedAccounts.source(), amount);
        return executeTransfer(lockedAccounts.source(), lockedAccounts.target(), amount, ledgerSpec);
    }

    /**
     * 호출 트랜잭션이 이미 잠근 계좌를 재조회하지 않고 지정 금액만큼 이체한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TransferResult transferLocked(
            Account source,
            Account target,
            Long amount,
            TransferLedgerSpec ledgerSpec
    ) {
        validateAmount(amount);
        validateLockedAccounts(source, target);
        validateSufficientBalance(source, amount);
        return executeTransfer(source, target, amount, ledgerSpec);
    }

    /**
     * 잠금 후 확정한 출금 계좌의 현재 잔액 전액을 이체해 잠금 전 조회값 사용을 피한다.
     */
    @Transactional
    public TransferResult transferAll(
            Long sourceAccountId,
            Long targetAccountId,
            TransferLedgerSpec ledgerSpec
    ) {
        LockedAccounts lockedAccounts = lockAndValidateAccounts(sourceAccountId, targetAccountId);
        Long amount = lockedAccounts.source().getBalance();
        validateSufficientBalance(lockedAccounts.source(), amount);
        return executeTransfer(lockedAccounts.source(), lockedAccounts.target(), amount, ledgerSpec);
    }

    /**
     * 호출 트랜잭션이 이미 잠근 출금 계좌의 현재 잔액 전액을 재조회 없이 이체한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TransferResult transferAllLocked(
            Account source,
            Account target,
            TransferLedgerSpec ledgerSpec
    ) {
        validateLockedAccounts(source, target);
        Long amount = source.getBalance();
        validateSufficientBalance(source, amount);
        return executeTransfer(source, target, amount, ledgerSpec);
    }

    /**
     * 계좌 ID 오름차순으로 두 계좌를 잠그고 거래 가능한 상태인지 확인한다.
     */
    private LockedAccounts lockAndValidateAccounts(Long sourceAccountId, Long targetAccountId) {
        if (sourceAccountId == null || targetAccountId == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (sourceAccountId.equals(targetAccountId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_TRANSFERABLE);
        }

        List<Long> sortedIds = sourceAccountId < targetAccountId
                ? List.of(sourceAccountId, targetAccountId)
                : List.of(targetAccountId, sourceAccountId);
        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdateOrderByAccountId(sortedIds);
        if (lockedAccounts.size() != 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        Account source = findLockedAccount(lockedAccounts, sourceAccountId);
        Account target = findLockedAccount(lockedAccounts, targetAccountId);
        validateLockedAccounts(source, target);
        return new LockedAccounts(source, target);
    }

    /**
     * 이미 잠긴 계좌의 식별자와 거래 가능 상태를 공통 검증한다.
     */
    private void validateLockedAccounts(Account source, Account target) {
        if (source == null || target == null
                || source.getAccountId() == null || target.getAccountId() == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (source.getAccountId().equals(target.getAccountId())
                || source.getAccountStatus() != AccountStatus.ACTIVE
                || target.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_TRANSFERABLE);
        }
    }

    /**
     * 잠긴 계좌 목록에서 출금 또는 입금 계좌를 ID로 찾는다.
     */
    private Account findLockedAccount(List<Account> lockedAccounts, Long accountId) {
        return lockedAccounts.stream()
                .filter(account -> account.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /**
     * 부분 이체 요청 금액이 양수인지 검증한다.
     */
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    /**
     * 잠금 후 확정한 출금 계좌 잔액으로 출금 가능 여부를 판단한다.
     */
    private void validateSufficientBalance(Account source, Long amount) {
        if (amount == null || amount <= 0L || source.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        }
    }

    /**
     * 계좌 잔액, 금융거래 한 행과 입출금 원장 두 행을 한 트랜잭션에 반영한다.
     */
    private TransferResult executeTransfer(
            Account source,
            Account target,
            Long amount,
            TransferLedgerSpec ledgerSpec
    ) {
        if (ledgerSpec == null) {
            throw new IllegalArgumentException("ledgerSpec must not be null");
        }

        Long sourceBalanceBefore = source.getBalance();
        Long targetBalanceBefore = target.getBalance();
        LocalDateTime executionDatetime = LocalDateTime.now();
        Long transactionId = snowflake.nextId();

        source.debit(amount);
        target.credit(amount);

        FinancialTransaction transaction = FinancialTransaction.create(
                transactionId,
                TransactionType.TRANSFER,
                amount,
                executionDatetime
        );
        AccountEntry withdrawalEntry = AccountEntry.withdrawal(
                snowflake.nextId(),
                source.getAccountId(),
                transactionId,
                ledgerSpec.sourceEntryCode(),
                amount,
                sourceBalanceBefore,
                executionDatetime,
                ledgerSpec.sourceDescription()
        );
        AccountEntry depositEntry = AccountEntry.deposit(
                snowflake.nextId(),
                target.getAccountId(),
                transactionId,
                ledgerSpec.targetEntryCode(),
                amount,
                targetBalanceBefore,
                executionDatetime,
                ledgerSpec.targetDescription()
        );

        financialTransactionRepository.save(transaction);
        accountEntryRepository.saveAll(List.of(withdrawalEntry, depositEntry));

        log.debug(
                "당행 이체의 거래·원장 반영을 완료했습니다. transactionId={}, sourceAccountId={}, targetAccountId={}",
                transactionId,
                source.getAccountId(),
                target.getAccountId()
        );

        return new TransferResult(
                transactionId,
                amount,
                source.getBalance(),
                target.getBalance()
        );
    }

    /**
     * 요청 방향을 유지한 출금·입금 계좌 쌍이다.
     */
    private record LockedAccounts(Account source, Account target) {
    }
}
