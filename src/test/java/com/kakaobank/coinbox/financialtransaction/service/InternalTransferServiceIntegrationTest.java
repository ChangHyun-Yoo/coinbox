package com.kakaobank.coinbox.financialtransaction.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountentry.entity.AccountEntry;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
import com.kakaobank.coinbox.accountentry.entity.EntryType;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.financialtransaction.entity.FinancialTransaction;
import com.kakaobank.coinbox.financialtransaction.entity.TransactionStatus;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
@DisplayName("당행 이체 통합 테스트")
class InternalTransferServiceIntegrationTest {

    private static final TransferLedgerSpec LEDGER_SPEC = new TransferLedgerSpec(
            EntryCode.COINBOX_EMPTY,
            "비우기",
            EntryCode.COINBOX,
            "저금통"
    );

    @Autowired
    private InternalTransferService internalTransferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @BeforeEach
    void cleanDatabase() {
        accountEntryRepository.deleteAllInBatch();
        financialTransactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("두 잔액과 금융거래 한 건 및 계좌 원장 두 건을 함께 Commit한다")
    void commitsBalancesTransactionAndEntriesTogether() {
        // given: 잔액이 있는 저금통과 연결 입출금계좌를 저장한다.
        Account source = account(100L, ProductType.COINBOX, "3310000000100", 10_000L);
        Account target = account(200L, ProductType.DEMAND_DEPOSIT, "3333000000200", 2_000L);
        accountRepository.saveAllAndFlush(List.of(source, target));

        // when: 저금통에서 입출금계좌로 4,000원을 이체한다.
        TransferResult result = internalTransferService.transfer(100L, 200L, 4_000L, LEDGER_SPEC);

        // then: 두 잔액과 성공 거래, 출금·입금 원장이 모두 DB에 반영된다.
        assertThat(accountRepository.findById(100L).orElseThrow().getBalance()).isEqualTo(6_000L);
        assertThat(accountRepository.findById(200L).orElseThrow().getBalance()).isEqualTo(6_000L);

        FinancialTransaction transaction = financialTransactionRepository
                .findById(result.transactionId())
                .orElseThrow();
        assertThat(transaction)
                .returns(4_000L, FinancialTransaction::getTransactionAmount)
                .returns(TransactionStatus.SUCCESS, FinancialTransaction::getTransactionStatus);

        List<AccountEntry> entries = accountEntryRepository.findAll();
        assertThat(entries)
                .hasSize(2)
                .extracting(AccountEntry::getEntryType)
                .containsExactlyInAnyOrder(EntryType.WITHDRAWAL, EntryType.DEPOSIT);
        assertThat(entries)
                .extracting(AccountEntry::getTransactionId)
                .containsOnly(result.transactionId());
    }

    @Test
    @DisplayName("동시 출금은 잠금 후 잔액을 재검증해 잔액 유실과 초과 출금을 막는다")
    void preventsLostUpdateAndOverdraftDuringConcurrentTransfers() throws Exception {
        // given: 100원의 출금 계좌에서 서로 다른 두 계좌로 각각 70원을 보내려 한다.
        Account source = account(100L, ProductType.DEMAND_DEPOSIT, "3333000000100", 100L);
        Account firstTarget = account(200L, ProductType.DEMAND_DEPOSIT, "3333000000200", 0L);
        Account secondTarget = account(300L, ProductType.DEMAND_DEPOSIT, "3333000000300", 0L);
        accountRepository.saveAllAndFlush(List.of(source, firstTarget, secondTarget));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ErrorCode> first = executorService.submit(
                    () -> transferAfterSignal(100L, 200L, ready, start)
            );
            Future<ErrorCode> second = executorService.submit(
                    () -> transferAfterSignal(100L, 300L, ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ErrorCode> results = Arrays.asList(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            // then: 한 건만 성공하고 나머지는 잠금 후 확인한 잔액 부족으로 실패한다.
            assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }

        Account updatedSource = accountRepository.findById(100L).orElseThrow();
        Account updatedFirstTarget = accountRepository.findById(200L).orElseThrow();
        Account updatedSecondTarget = accountRepository.findById(300L).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualTo(30L);
        assertThat(updatedFirstTarget.getBalance() + updatedSecondTarget.getBalance()).isEqualTo(70L);
        assertThat(updatedSource.getBalance()
                + updatedFirstTarget.getBalance()
                + updatedSecondTarget.getBalance()).isEqualTo(100L);
        assertThat(financialTransactionRepository.count()).isOne();
        assertThat(accountEntryRepository.count()).isEqualTo(2L);
    }

    private ErrorCode transferAfterSignal(
            Long sourceAccountId,
            Long targetAccountId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            internalTransferService.transfer(sourceAccountId, targetAccountId, 70L, LEDGER_SPEC);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private Account account(Long accountId, ProductType productType, String accountNumber, Long balance) {
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
}
