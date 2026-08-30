package com.kakaobank.coinbox.financialtransaction.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
import com.kakaobank.coinbox.accountentry.entity.EntryType;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.common.snowflake.Snowflake;
import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionStatus;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionType;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("당행 이체 Service")
class InternalTransferServiceTest {

    private static final TransferLedgerSpec LEDGER_SPEC = new TransferLedgerSpec(
            EntryCode.COINBOX_EMPTY,
            "비우기",
            EntryCode.COINBOX,
            "저금통"
    );

    private AccountRepository accountRepository;
    private FinancialTransactionRepository financialTransactionRepository;
    private AccountEntryRepository accountEntryRepository;
    private InternalTransferService internalTransferService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        financialTransactionRepository = mock(FinancialTransactionRepository.class);
        accountEntryRepository = mock(AccountEntryRepository.class);
        internalTransferService = new InternalTransferService(
                accountRepository,
                financialTransactionRepository,
                accountEntryRepository,
                new Snowflake(1L)
        );
    }

    @Test
    @DisplayName("두 계좌를 ID 오름차순으로 잠근 뒤 거래와 출입금 원장을 생성한다")
    void transfersMoneyAndCreatesTransactionLedgerEntries() {
        // given: 출금 계좌 ID가 입금 계좌 ID보다 큰 정상 계좌 두 개를 준비한다.
        Account source = account(20L, "3310000000020", 10_000L);
        Account target = account(10L, "3333000000010", 2_000L);
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L)))
                .thenReturn(List.of(target, source));

        // when: 4,000원을 당행 이체한다.
        TransferResult result = internalTransferService.transfer(20L, 10L, 4_000L, LEDGER_SPEC);

        // then: 잔액이 보존되고 하나의 성공 거래와 서로 반대 방향인 두 원장이 생성된다.
        assertThat(result)
                .returns(4_000L, TransferResult::amount)
                .returns(6_000L, TransferResult::sourceBalanceAfter)
                .returns(6_000L, TransferResult::targetBalanceAfter);
        verify(accountRepository).findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L));

        ArgumentCaptor<FinancialTransaction> transactionCaptor =
                ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(transactionCaptor.capture());
        FinancialTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction)
                .returns(result.transactionId(), FinancialTransaction::getTransactionId)
                .returns(TransactionType.TRANSFER, FinancialTransaction::getTransactionType)
                .returns(TransactionStatus.SUCCESS, FinancialTransaction::getTransactionStatus)
                .returns(4_000L, FinancialTransaction::getTransactionAmount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<AccountEntry>> entriesCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(accountEntryRepository).saveAll(entriesCaptor.capture());
        List<AccountEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();
        assertThat(entries)
                .hasSize(2)
                .extracting(AccountEntry::getEntryType)
                .containsExactly(EntryType.WITHDRAWAL, EntryType.DEPOSIT);
        assertThat(entries)
                .extracting(AccountEntry::getTransactionId)
                .containsOnly(result.transactionId());
        assertThat(entries)
                .extracting(AccountEntry::getTransactionDatetime)
                .containsOnly(transaction.getExecutionDatetime());
    }

    @Test
    @DisplayName("전액 이체 금액은 잠금 후 출금 계좌의 최신 잔액으로 결정한다")
    void transfersAllOfLockedSourceBalance() {
        // given: 잠금 후 잔액이 4,360원인 출금 계좌를 준비한다.
        Account source = account(10L, "3310000000010", 4_360L);
        Account target = account(20L, "3333000000020", 100_000L);
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L)))
                .thenReturn(List.of(source, target));

        // when: 출금 계좌 잔액 전액을 이체한다.
        TransferResult result = internalTransferService.transferAll(10L, 20L, LEDGER_SPEC);

        // then: 잠긴 잔액 전액이 이동한다.
        assertThat(result)
                .returns(4_360L, TransferResult::amount)
                .returns(0L, TransferResult::sourceBalanceAfter)
                .returns(104_360L, TransferResult::targetBalanceAfter);
    }

    @Test
    @DisplayName("호출자가 이미 잠근 계좌는 재조회하지 않고 지정 금액을 이체한다")
    void transfersWithAlreadyLockedAccountsWithoutAnotherQuery() {
        // given: 호출 트랜잭션에서 잠금과 검증을 마친 두 ACTIVE 계좌다.
        Account source = account(10L, "3333000000010", 10_000L);
        Account target = account(20L, "3310000000020", 2_000L);

        // when: 이미 잠긴 계좌로 4,000원을 이체한다.
        TransferResult result = internalTransferService.transferLocked(source, target, 4_000L, LEDGER_SPEC);

        // then: 계좌를 다시 조회하지 않고 전달된 최신 잔액에 거래를 반영한다.
        assertThat(result)
                .returns(4_000L, TransferResult::amount)
                .returns(6_000L, TransferResult::sourceBalanceAfter)
                .returns(6_000L, TransferResult::targetBalanceAfter);
        verify(accountRepository, never()).findAllByIdForUpdateOrderByAccountId(any());
    }

    @Test
    @DisplayName("호출자가 이미 잠근 출금 계좌의 잔액 전액을 재조회 없이 이체한다")
    void transfersAllWithAlreadyLockedAccountsWithoutAnotherQuery() {
        // given: 호출 트랜잭션에서 잠금과 검증을 마친 두 ACTIVE 계좌다.
        Account source = account(10L, "3310000000010", 4_360L);
        Account target = account(20L, "3333000000020", 100_000L);

        // when: 이미 잠긴 출금 계좌의 전액을 이체한다.
        TransferResult result = internalTransferService.transferAllLocked(source, target, LEDGER_SPEC);

        // then: 재조회 없이 현재 잔액 전액이 이동한다.
        assertThat(result)
                .returns(4_360L, TransferResult::amount)
                .returns(0L, TransferResult::sourceBalanceAfter)
                .returns(104_360L, TransferResult::targetBalanceAfter);
        verify(accountRepository, never()).findAllByIdForUpdateOrderByAccountId(any());
    }

    @Test
    @DisplayName("이미 잠긴 계좌 경로에서도 식별자와 ACTIVE 상태를 다시 검증한다")
    void rejectsInvalidAlreadyLockedAccounts() {
        // given: 동일 계좌와 해지 계좌가 포함된 잘못된 호출이다.
        Account active = account(10L, "3333000000010", 10_000L);
        Account closed = account(20L, "3310000000020", 0L);
        closed.close();

        // when & then: 공통 거래 조건을 만족하지 않으면 금융거래를 생성하지 않는다.
        assertBusinessException(
                () -> internalTransferService.transferLocked(active, active, 1_000L, LEDGER_SPEC),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );
        assertBusinessException(
                () -> internalTransferService.transferLocked(active, closed, 1_000L, LEDGER_SPEC),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("두 계좌 중 하나라도 존재하지 않으면 이체하지 않는다")
    void rejectsTransferWhenAnAccountDoesNotExist() {
        // given: 잠금 조회 결과가 출금 계좌 한 건뿐이다.
        Account source = account(10L, "3310000000010", 10_000L);
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L)))
                .thenReturn(List.of(source));

        // when & then: 계좌 없음 예외가 발생하고 거래는 생성되지 않는다.
        assertBusinessException(
                () -> internalTransferService.transfer(10L, 20L, 1_000L, LEDGER_SPEC),
                ErrorCode.ACCOUNT_NOT_FOUND
        );
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("동일 계좌 사이의 이체를 거부한다")
    void rejectsTransferToSameAccount() {
        // when & then: 잠금을 시도하기 전에 거래 불가 예외를 반환한다.
        assertBusinessException(
                () -> internalTransferService.transfer(10L, 10L, 1_000L, LEDGER_SPEC),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );
        verify(accountRepository, never()).findAllByIdForUpdateOrderByAccountId(any());
    }

    @Test
    @DisplayName("두 계좌 중 하나라도 ACTIVE가 아니면 이체하지 않는다")
    void rejectsTransferWhenAnAccountIsNotActive() {
        // given: 입금 계좌가 이미 해지된 상태다.
        Account source = account(10L, "3310000000010", 10_000L);
        Account closedTarget = account(20L, "3333000000020", 0L);
        closedTarget.close();
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L)))
                .thenReturn(List.of(source, closedTarget));

        // when & then: 계좌 거래 불가 예외가 발생하고 잔액은 바뀌지 않는다.
        assertBusinessException(
                () -> internalTransferService.transfer(10L, 20L, 1_000L, LEDGER_SPEC),
                ErrorCode.ACCOUNT_NOT_TRANSFERABLE
        );
        assertThat(source.getBalance()).isEqualTo(10_000L);
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("출금 잔액이 부족하면 이체하지 않는다")
    void rejectsTransferWhenSourceBalanceIsInsufficient() {
        // given: 출금 잔액이 이체 금액보다 적다.
        Account source = account(10L, "3310000000010", 500L);
        Account target = account(20L, "3333000000020", 1_000L);
        when(accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(10L, 20L)))
                .thenReturn(List.of(source, target));

        // when & then: 잔액 부족 예외가 발생하고 두 잔액은 유지된다.
        assertBusinessException(
                () -> internalTransferService.transfer(10L, 20L, 1_000L, LEDGER_SPEC),
                ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE
        );
        assertThat(source.getBalance()).isEqualTo(500L);
        assertThat(target.getBalance()).isEqualTo(1_000L);
        verify(financialTransactionRepository, never()).save(any());
    }

    private Account account(Long accountId, String accountNumber, Long balance) {
        ProductType productType = accountNumber.startsWith("3310")
                ? ProductType.COINBOX
                : ProductType.DEMAND_DEPOSIT;
        return Account.create(
                accountId,
                1L,
                productType,
                accountNumber,
                null,
                balance,
                LocalDate.of(2026, 8, 28)
        );
    }

    private void assertBusinessException(Runnable operation, ErrorCode expectedErrorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
