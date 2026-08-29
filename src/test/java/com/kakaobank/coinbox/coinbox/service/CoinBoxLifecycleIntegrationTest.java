package com.kakaobank.coinbox.coinbox.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.response.ActiveAccountResponse;
import com.kakaobank.coinbox.account.service.AccountQueryService;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.accountcontract.repository.AccountContractRepository;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.customer.entity.Customer;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({MySqlTestContainer.class, CoinBoxLifecycleIntegrationTest.FixedClockConfiguration.class})
@DisplayName("저금통 온라인 생명주기 통합 테스트")
class CoinBoxLifecycleIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);
    private static final Long CUSTOMER_ID = 1L;
    private static final Long PARENT_ACCOUNT_ID = 10L;

    @Autowired
    private CoinBoxService coinBoxService;

    @Autowired
    private AccountQueryService accountQueryService;

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
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @BeforeEach
    void setUp() {
        accountEntryRepository.deleteAllInBatch();
        financialTransactionRepository.deleteAllInBatch();
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
    @DisplayName("가입 가능 조회부터 개설·계좌 조회·비우기·해지까지 원자적으로 처리한다")
    void processesCompleteOnlineLifecycle() {
        // given: 고객에게 가입 가능한 정상 입출금계좌와 유효한 저금통 정책이 있다.
        assertThat(coinBoxService.findEligibleAccounts(CUSTOMER_ID))
                .extracting(Account::getAccountId)
                .containsExactly(PARENT_ACCOUNT_ID);

        // when: 저금통을 신규 개설한다.
        CoinBoxOpenResult openResult = coinBoxService.openCoinBox(CUSTOMER_ID, PARENT_ACCOUNT_ID);
        Long coinBoxAccountId = openResult.account().getAccountId();

        // then: 계좌·계약·설정이 생성되고 ACTIVE 계좌 조회에서 자식 저금통으로 반환된다.
        assertThat(openResult.account().getAccountNumber()).matches("3310[0-9]{9}");
        assertThat(accountContractRepository.count()).isOne();
        assertThat(coinBoxRepository.count()).isOne();
        List<ActiveAccountResponse> activeAccounts = accountQueryService.findActiveAccounts(CUSTOMER_ID);
        assertThat(activeAccounts).singleElement();
        assertThat(activeAccounts.getFirst().childAccount())
                .singleElement()
                .returns(String.valueOf(coinBoxAccountId), child -> child.accountId())
                .returns("저금통", child -> child.productName());

        // and: 같은 고객의 중복 개설은 잠금 후 거부한다.
        assertThatThrownBy(() -> coinBoxService.openCoinBox(CUSTOMER_ID, PARENT_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COINBOX_ALREADY_EXISTS);

        // when: 저금통에 잔액을 준비하고 전액 비운다.
        addBalance(coinBoxAccountId, 4_360L);
        TransferResult emptyResult = coinBoxService.emptyCoinBox(
                CUSTOMER_ID,
                openResult.account().getAccountNumber()
        );

        // then: 잔액과 거래·원장이 함께 반영된다.
        assertThat(emptyResult.amount()).isEqualTo(4_360L);
        assertThat(accountRepository.findById(coinBoxAccountId).orElseThrow().getBalance()).isZero();
        assertThat(accountRepository.findById(PARENT_ACCOUNT_ID).orElseThrow().getBalance()).isEqualTo(104_360L);
        assertThat(financialTransactionRepository.count()).isOne();
        assertThat(accountEntryRepository.count()).isEqualTo(2L);

        // when: 다시 남은 잔액이 있는 상태에서 해지한다.
        addBalance(coinBoxAccountId, 2_500L);
        CoinBoxTerminationResult terminationResult = coinBoxService.terminateCoinBox(
                CUSTOMER_ID,
                openResult.account().getAccountNumber()
        );

        // then: 잔액 이전과 계좌·계약·설정 종료가 모두 Commit된다.
        Account terminatedAccount = accountRepository.findById(coinBoxAccountId).orElseThrow();
        AccountContract terminatedContract = accountContractRepository.findAll().getFirst();
        CoinBox terminatedCoinBox = coinBoxRepository.findAll().getFirst();
        assertThat(terminationResult.transferredAmount()).isEqualTo(2_500L);
        assertThat(terminatedAccount.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(terminatedAccount.getBalance()).isZero();
        assertThat(terminatedContract)
                .returns(ContractStatus.TERMINATED, AccountContract::getContractStatus)
                .returns(TODAY, AccountContract::getContractEndDate);
        assertThat(terminatedCoinBox.isCoinSavingEnabled()).isFalse();
        assertThat(terminatedCoinBox.getCoinSavingStartDate()).isNull();
        assertThat(accountRepository.findById(PARENT_ACCOUNT_ID).orElseThrow().getBalance()).isEqualTo(106_860L);
        assertThat(financialTransactionRepository.count()).isEqualTo(2L);
        assertThat(accountEntryRepository.count()).isEqualTo(4L);

        // and: 해지된 저금통은 ACTIVE 계좌 목록의 자식에서 제외된다.
        assertThat(accountQueryService.findActiveAccounts(CUSTOMER_ID).getFirst().childAccount()).isEmpty();
    }

    private void addBalance(Long accountId, Long amount) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(amount);
        accountRepository.saveAndFlush(account);
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
