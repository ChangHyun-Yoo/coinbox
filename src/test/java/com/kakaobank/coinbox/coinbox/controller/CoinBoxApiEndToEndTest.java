package com.kakaobank.coinbox.coinbox.controller;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.accountdailybalance.repository.AccountDailyBalanceRepository;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyRepository;
import com.kakaobank.coinbox.coinsavingexecution.repository.CoinSavingExecutionRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문서의 저금통 온라인 흐름을 HTTP 요청부터 MySQL 최종 데이터까지 연결해 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({MySqlTestContainer.class, CoinBoxApiEndToEndTest.FixedClockConfiguration.class})
@DisplayName("저금통 API End-to-End 통합 테스트")
class CoinBoxApiEndToEndTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);
    private static final Long CUSTOMER_ID = 1L;
    private static final Long PARENT_ACCOUNT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

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

    @BeforeEach
    void setUp() {
        accountEntryRepository.deleteAllInBatch();
        coinSavingExecutionRepository.deleteAllInBatch();
        financialTransactionRepository.deleteAllInBatch();
        accountDailyBalanceRepository.deleteAllInBatch();
        coinBoxRepository.deleteAllInBatch();
        accountContractRepository.deleteAllInBatch();
        coinBoxPolicyRepository.deleteAllInBatch();
        productVersionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        customerRepository.saveAndFlush(Customer.create(CUSTOMER_ID));
        productRepository.saveAllAndFlush(List.of(
                Product.create(100L, ProductType.DEMAND_DEPOSIT, "입출금통장"),
                Product.create(101L, ProductType.COINBOX, "저금통")
        ));
        productVersionRepository.saveAndFlush(ProductVersion.create(
                200L,
                101L,
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(9999, 12, 31)
        ));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(300L, 200L, 100_000L));
        accountRepository.saveAndFlush(Account.create(
                PARENT_ACCOUNT_ID,
                CUSTOMER_ID,
                ProductType.DEMAND_DEPOSIT,
                "3333000000010",
                null,
                100_000L,
                TODAY
        ));
    }

    @Test
    @DisplayName("HTTP로 가입 가능 조회부터 개설·계좌 조회·비우기·해지까지 처리한다")
    void processesNormalLifecycleThroughHttp() throws Exception {
        // given & when: 가입 가능한 계좌를 조회한다.
        mockMvc.perform(get("/api/v1/coinboxes/eligible-accounts")
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].accountId").value("10"))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value("3333000000010"));

        // when: 선택한 입출금계좌를 근거계좌로 저금통을 개설한다.
        mockMvc.perform(post("/api/v1/coinboxes")
                        .header("X-Customer-Id", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentAccountId\":\"10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("3310[0-9]{9}")))
                .andExpect(jsonPath("$.productType").value("COINBOX"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.coinSavingEnabled").value(true))
                .andExpect(jsonPath("$.coinSavingStartDate").value("2026-08-28"));

        Account coinBoxAccount = findCoinBoxAccount();

        // then: ACTIVE 계좌 API에서 개설한 저금통을 근거계좌의 자식으로 반환한다.
        mockMvc.perform(get("/api/v1/accounts").header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value("10"))
                .andExpect(jsonPath("$[0].childAccount[0].accountId")
                        .value(String.valueOf(coinBoxAccount.getAccountId())))
                .andExpect(jsonPath("$[0].childAccount[0].accountNumber")
                        .value(coinBoxAccount.getAccountNumber()));

        // when: 잔액이 있는 저금통을 비운다.
        addBalance(coinBoxAccount.getAccountId(), 4_360L);
        mockMvc.perform(post("/api/v1/coinboxes/{accountNumber}/empty", coinBoxAccount.getAccountNumber())
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(4_360L))
                .andExpect(jsonPath("$.coinBoxBalanceAfter").value(0L))
                .andExpect(jsonPath("$.parentAccountBalanceAfter").value(104_360L));

        // when: 다시 잔액이 생긴 저금통을 해지한다.
        addBalance(coinBoxAccount.getAccountId(), 2_500L);
        mockMvc.perform(delete("/api/v1/coinboxes/{accountNumber}", coinBoxAccount.getAccountNumber())
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
                .andExpect(jsonPath("$.contractStatus").value("TERMINATED"))
                .andExpect(jsonPath("$.transferredAmount").value(2_500L))
                .andExpect(jsonPath("$.terminationDate").value("2026-08-28"));

        // then: 두 번의 이체와 네 개의 원장이 남고 해지 계좌는 ACTIVE 목록에서 제외된다.
        assertThat(financialTransactionRepository.count()).isEqualTo(2L);
        assertThat(accountEntryRepository.count()).isEqualTo(4L);
        assertThat(accountRepository.findById(PARENT_ACCOUNT_ID).orElseThrow().getBalance())
                .isEqualTo(106_860L);
        assertThat(accountRepository.findById(coinBoxAccount.getAccountId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.CLOSED);
        assertThat(accountContractRepository.findAll()).singleElement()
                .returns(ContractStatus.TERMINATED, contract -> contract.getContractStatus());
        mockMvc.perform(get("/api/v1/accounts").header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].childAccount").isEmpty());
    }

    @Test
    @DisplayName("중복 가입·빈 저금통 비우기·중복 해지를 문서의 오류 응답으로 반환한다")
    void returnsDocumentedBusinessErrorsThroughHttp() throws Exception {
        // given: 저금통을 한 번 개설했다.
        performOpen().andExpect(status().isCreated());
        Account coinBoxAccount = findCoinBoxAccount();

        // when & then: 같은 고객의 중복 가입을 거부한다.
        performOpen()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COINBOX_ALREADY_EXISTS"));

        // and: 잔액이 없는 저금통은 비울 수 없다.
        mockMvc.perform(post("/api/v1/coinboxes/{accountNumber}/empty", coinBoxAccount.getAccountNumber())
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COINBOX_BALANCE_EMPTY"));

        // when: 잔액이 없는 저금통을 정상 해지한다.
        mockMvc.perform(delete("/api/v1/coinboxes/{accountNumber}", coinBoxAccount.getAccountNumber())
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferredAmount").value(0L));

        // then: 이미 해지된 저금통은 다시 해지할 수 없고 금융 데이터도 생성되지 않는다.
        mockMvc.perform(delete("/api/v1/coinboxes/{accountNumber}", coinBoxAccount.getAccountNumber())
                        .header("X-Customer-Id", CUSTOMER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COINBOX_ALREADY_TERMINATED"));
        assertThat(financialTransactionRepository.count()).isZero();
        assertThat(accountEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("동시 개설 요청은 고객 잠금으로 직렬화되어 저금통 한 개만 생성한다")
    void serializesConcurrentOpenRequestsWithCustomerLock() throws Exception {
        // given: 같은 고객과 근거계좌로 두 HTTP 요청이 동시에 진입한다.
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResult> first = executorService.submit(() -> performOpenAfterSignal(ready, start));
            Future<HttpResult> second = executorService.submit(() -> performOpenAfterSignal(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResult> results = Arrays.asList(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            // then: 한 요청만 성공하고 잠금을 뒤늦게 획득한 요청은 중복 가입으로 종료된다.
            assertThat(results)
                    .extracting(HttpResult::status)
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(results.stream()
                    .filter(result -> result.status() == 409)
                    .findFirst()
                    .orElseThrow()
                    .body()).contains("\"code\":\"COINBOX_ALREADY_EXISTS\"");
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }

        // and: 계좌·계약·저금통은 각각 한 행만 Commit되어 부분 생성이나 중복 생성이 없다.
        assertThat(accountRepository.findAll().stream()
                .filter(account -> account.getProductType() == ProductType.COINBOX)).hasSize(1);
        assertThat(accountContractRepository.count()).isOne();
        assertThat(coinBoxRepository.count()).isOne();
    }

    private org.springframework.test.web.servlet.ResultActions performOpen() throws Exception {
        return mockMvc.perform(post("/api/v1/coinboxes")
                .header("X-Customer-Id", CUSTOMER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentAccountId\":\"10\"}"));
    }

    private HttpResult performOpenAfterSignal(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = performOpen().andReturn();
        return new HttpResult(
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString()
        );
    }

    private Account findCoinBoxAccount() {
        return accountRepository.findAll().stream()
                .filter(account -> account.getProductType() == ProductType.COINBOX)
                .findFirst()
                .orElseThrow();
    }

    private void addBalance(Long accountId, Long amount) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(amount);
        accountRepository.saveAndFlush(account);
    }

    private record HttpResult(int status, String body) {
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
