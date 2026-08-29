package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.accountdailybalance.entity.AccountDailyBalance;
import com.kakaobank.coinbox.accountdailybalance.repository.AccountDailyBalanceRepository;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyRepository;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecution;
import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingExecutionStatus;
import com.kakaobank.coinbox.coinsavingexecution.batch.CoinSavingCandidate;
import com.kakaobank.coinbox.coinsavingexecution.repository.CoinSavingExecutionRepository;
import com.kakaobank.coinbox.common.batch.BatchScheduler;
import com.kakaobank.coinbox.customer.entity.Customer;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.product.entity.ProductVersion;
import com.kakaobank.coinbox.product.repository.ProductRepository;
import com.kakaobank.coinbox.product.repository.ProductVersionRepository;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({MySqlTestContainer.class, BatchLifecycleIntegrationTest.FixedClockConfiguration.class})
@DisplayName("저금통 배치 생명주기 통합 테스트")
class BatchLifecycleIntegrationTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 8, 28);
    private static final LocalDate PREVIOUS_DATE = EXECUTION_DATE.minusDays(1);

    @Autowired
    private BatchScheduler batchScheduler;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVersionRepository productVersionRepository;

    @Autowired
    private CoinBoxPolicyRepository coinBoxPolicyRepository;

    @Autowired
    private AccountContractRepository accountContractRepository;

    @Autowired
    private CoinBoxRepository coinBoxRepository;

    @Autowired
    private AccountDailyBalanceRepository accountDailyBalanceRepository;

    @Autowired
    private CoinSavingExecutionRepository coinSavingExecutionRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @Autowired
    private CoinSavingService coinSavingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dropFailureConstraint();
        accountEntryRepository.deleteAllInBatch();
        coinSavingExecutionRepository.deleteAllInBatch();
        financialTransactionRepository.deleteAllInBatch();
        accountDailyBalanceRepository.deleteAllInBatch();
        coinBoxRepository.deleteAllInBatch();
        accountContractRepository.deleteAllInBatch();
        coinBoxPolicyRepository.deleteAllInBatch();
        productVersionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        customerRepository.saveAndFlush(Customer.create(1L));
        productRepository.saveAllAndFlush(List.of(
                Product.create(2L, ProductType.DEMAND_DEPOSIT, "입출금통장"),
                Product.create(3L, ProductType.COINBOX, "저금통")
        ));
        productVersionRepository.saveAndFlush(ProductVersion.create(
                4L, 3L, 1, LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)
        ));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(5L, 4L, 100_000L));
        accountRepository.saveAllAndFlush(List.of(
                account(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", null, 100_850L),
                account(11L, ProductType.COINBOX, "3310000000011", 10L, 500L)
        ));
        accountContractRepository.saveAndFlush(AccountContract.create(20L, 11L, 4L, PREVIOUS_DATE));
        coinBoxRepository.saveAndFlush(CoinBox.create(30L, 11L, PREVIOUS_DATE));
    }

    @AfterEach
    void dropFailureCheckConstraint() {
        dropFailureConstraint();
    }

    @Test
    @DisplayName("일별 잔액 생성 후 동전모으기를 실행하고 같은 기준일 재실행은 중복 반영하지 않는다")
    void runsDailyBalanceAndCoinSavingJobsIdempotently() {
        // given: 전일 잔돈이 850원이고 동전모으기 가능한 저금통이 있다.

        // when: 일별 잔액과 동전모으기를 실행한 뒤 두 Job을 같은 기준일로 다시 실행한다.
        JobExecution firstDailyBalance = batchScheduler.launchDailyBalanceJob(EXECUTION_DATE);
        JobExecution firstCoinSaving = batchScheduler.launchCoinSavingJob(EXECUTION_DATE);
        JobExecution secondDailyBalance = batchScheduler.launchDailyBalanceJob(EXECUTION_DATE);
        JobExecution secondCoinSaving = batchScheduler.launchCoinSavingJob(EXECUTION_DATE);

        // then: 모든 Job이 완료되고 최초 실행 데이터만 유지된다.
        assertThat(List.of(firstDailyBalance, firstCoinSaving, secondDailyBalance, secondCoinSaving))
                .allSatisfy(execution -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED));
        assertThat(accountDailyBalanceRepository.findAll()).hasSize(2);
        AccountDailyBalance parentSnapshot = accountDailyBalanceRepository.findAll().stream()
                .filter(balance -> balance.getAccountId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(parentSnapshot)
                .returns(PREVIOUS_DATE, AccountDailyBalance::getBalanceDate)
                .returns(100_850L, AccountDailyBalance::getClosingBalance);

        assertThat(accountRepository.findById(10L).orElseThrow().getBalance()).isEqualTo(100_000L);
        assertThat(accountRepository.findById(11L).orElseThrow().getBalance()).isEqualTo(1_350L);
        assertThat(financialTransactionRepository.count()).isEqualTo(1L);
        assertThat(accountEntryRepository.count()).isEqualTo(2L);
        assertThat(coinSavingExecutionRepository.findAll())
                .singleElement()
                .returns(CoinSavingExecutionStatus.SUCCESS, CoinSavingExecution::getExecutionStatus)
                .returns(850L, CoinSavingExecution::getSavingAmount);
    }

    @Test
    @DisplayName("동일 저금통의 동시 실행은 계좌와 저금통 잠금 후 한 번만 이체한다")
    void savesCoinsOnceDuringConcurrentExecutions() throws Exception {
        // given: 두 배치 실행이 동일한 저금통과 실행일 후보를 동시에 처리한다.
        CoinSavingCandidate candidate = new CoinSavingCandidate(
                30L, 11L, 10L, 100_850L, EXECUTION_DATE
        );
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CoinSavingResult> first = executorService.submit(
                    () -> executeCoinSavingAfterSignal(candidate, ready, start)
            );
            Future<CoinSavingResult> second = executorService.submit(
                    () -> executeCoinSavingAfterSignal(candidate, ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CoinSavingResultStatus> statuses = Arrays.asList(
                    first.get(15, TimeUnit.SECONDS).status(),
                    second.get(15, TimeUnit.SECONDS).status()
            );

            // then: 먼저 잠근 실행만 이체하고 다음 실행은 잠금 후 실행 이력을 재확인해 중복으로 끝난다.
            assertThat(statuses).containsExactlyInAnyOrder(
                    CoinSavingResultStatus.SUCCESS,
                    CoinSavingResultStatus.DUPLICATE
            );
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }

        // and: 잔액·거래·원장·실행 이력은 단 한 번의 동전모으기 결과와 일치한다.
        assertThat(accountRepository.findById(10L).orElseThrow().getBalance()).isEqualTo(100_000L);
        assertThat(accountRepository.findById(11L).orElseThrow().getBalance()).isEqualTo(1_350L);
        assertThat(financialTransactionRepository.count()).isOne();
        assertThat(accountEntryRepository.count()).isEqualTo(2L);
        assertThat(coinSavingExecutionRepository.findAll()).singleElement()
                .returns(CoinSavingExecutionStatus.SUCCESS, CoinSavingExecution::getExecutionStatus)
                .returns(850L, CoinSavingExecution::getSavingAmount);
    }

    @Test
    @DisplayName("동일 기준일의 동시 잔액 배치는 복합 UK로 계좌별 한 행만 유지한다")
    void keepsOneDailyBalancePerAccountDuringConcurrentJobs() throws Exception {
        // given: 두 Job이 같은 계좌와 같은 기준일의 일별 잔액을 동시에 저장하려 한다.
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<JobExecution> first = executorService.submit(
                    () -> launchDailyBalanceAfterSignal(ready, start)
            );
            Future<JobExecution> second = executorService.submit(
                    () -> launchDailyBalanceAfterSignal(ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<JobExecution> executions = Arrays.asList(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );

            // then: 두 Job 모두 교착 없이 완료된다.
            assertThat(executions)
                    .allSatisfy(execution -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED));
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }

        // and: (account_id, balance_date) 복합 UK와 중복 무시 쓰기로 계좌마다 한 행만 남는다.
        assertThat(accountDailyBalanceRepository.findAll())
                .hasSize(2)
                .allSatisfy(balance -> assertThat(balance.getBalanceDate()).isEqualTo(PREVIOUS_DATE))
                .extracting(AccountDailyBalance::getAccountId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("실행 이력 저장 실패 시 이체·원장·잔액을 모두 Rollback한다")
    void rollsBackCandidateWhenExecutionHistoryInsertFails() {
        // given: 금융 이체 뒤 실행 이력 INSERT만 실패하도록 DB 오류를 강제한다.
        jdbcTemplate.execute("""
                alter table coin_saving_execution
                add constraint chk_forced_execution_failure check (coinbox_id <> 30)
                """);
        CoinSavingCandidate candidate = new CoinSavingCandidate(
                30L, 11L, 10L, 100_850L, EXECUTION_DATE
        );

        // when & then: 후보 트랜잭션 Commit이 실패한다.
        assertThatThrownBy(() -> coinSavingService.execute(candidate))
                .isInstanceOf(RuntimeException.class);

        // then: 이체 전 잔액과 빈 거래·원장·실행 이력이 유지된다.
        assertThat(accountRepository.findById(10L).orElseThrow().getBalance()).isEqualTo(100_850L);
        assertThat(accountRepository.findById(11L).orElseThrow().getBalance()).isEqualTo(500L);
        assertThat(financialTransactionRepository.count()).isZero();
        assertThat(accountEntryRepository.count()).isZero();
        assertThat(coinSavingExecutionRepository.count()).isZero();
    }

    private void dropFailureConstraint() {
        try {
            jdbcTemplate.execute("""
                    alter table coin_saving_execution
                    drop check chk_forced_execution_failure
                    """);
        } catch (DataAccessException ignored) {
            // 해당 제약은 실패 검증 테스트에서만 임시 생성하므로 없으면 정리할 대상이 없다.
        }
    }

    private CoinSavingResult executeCoinSavingAfterSignal(
            CoinSavingCandidate candidate,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return coinSavingService.execute(candidate);
    }

    private JobExecution launchDailyBalanceAfterSignal(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return batchScheduler.launchDailyBalanceJob(EXECUTION_DATE);
    }

    private Account account(
            Long accountId,
            ProductType productType,
            String accountNumber,
            Long parentAccountId,
            Long balance
    ) {
        return Account.create(
                accountId,
                1L,
                productType,
                accountNumber,
                parentAccountId,
                balance,
                EXECUTION_DATE.minusDays(2)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-28T01:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
