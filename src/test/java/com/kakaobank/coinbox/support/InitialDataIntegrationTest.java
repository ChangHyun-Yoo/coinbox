package com.kakaobank.coinbox.support;

import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountdailybalance.repository.AccountDailyBalanceRepository;
import com.kakaobank.coinbox.coinbox.repository.CoinBoxRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyRepository;
import com.kakaobank.coinbox.customer.repository.CustomerRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 실행과 같은 JPA 스키마 생성 이후 data.sql 샘플 데이터 적재를 검증한다.
 */
@SpringBootTest
@Import(MySqlTestContainer.class)
@TestPropertySource(properties = "spring.sql.init.mode=always")
@DisplayName("초기 샘플 데이터 통합 테스트")
class InitialDataIntegrationTest {

    private static final Long EXISTING_COINBOX_CUSTOMER_ID = 700000000000000001L;
    private static final Long ELIGIBLE_ACCOUNT_CUSTOMER_ID = 700000000000000002L;
    private static final Long OPEN_COINBOX_CUSTOMER_ID = 700000000000000003L;
    private static final Long TERMINATE_COINBOX_CUSTOMER_ID = 700000000000000004L;
    private static final String COINBOX_ACCOUNT_NUMBER = "3310000000001";
    private static final String TERMINATION_COINBOX_ACCOUNT_NUMBER = "3310000000002";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CoinBoxPolicyRepository coinBoxPolicyRepository;

    @Autowired
    private CoinBoxRepository coinBoxRepository;

    @Autowired
    private AccountDailyBalanceRepository accountDailyBalanceRepository;

    /**
     * 초기 데이터가 주요 온라인·배치 시나리오를 바로 실행할 수 있는 상태인지 확인한다.
     */
    @Test
    @DisplayName("JPA create 이후 API와 배치용 샘플 데이터를 적재한다")
    void loadsSampleDataAfterJpaSchemaCreation() {
        // given: Testcontainers MySQL에 Hibernate가 업무 테이블을 새로 생성했다.

        // when: Spring SQL 초기화가 classpath의 data.sql을 실행한다.

        // then: 각 Swagger 성공 예시에 사용할 고객이 서로 독립적으로 준비됩니다.
        assertThat(customerRepository.findById(EXISTING_COINBOX_CUSTOMER_ID)).isPresent();
        assertThat(customerRepository.findById(ELIGIBLE_ACCOUNT_CUSTOMER_ID)).isPresent();
        assertThat(customerRepository.findById(OPEN_COINBOX_CUSTOMER_ID)).isPresent();
        assertThat(customerRepository.findById(TERMINATE_COINBOX_CUSTOMER_ID)).isPresent();

        // and: 비우기·해지용 ACTIVE 저금통과 연결 설정이 생성된다.
        var coinBoxAccount = accountRepository
                .findByCustomerIdAndAccountNumber(EXISTING_COINBOX_CUSTOMER_ID, COINBOX_ACCOUNT_NUMBER)
                .orElseThrow();
        assertThat(coinBoxAccount.getProductType()).isEqualTo(ProductType.COINBOX);
        assertThat(coinBoxAccount.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(coinBoxRepository.findAll()).anySatisfy(coinBox -> assertThat(coinBox)
                .returns(true, setting -> setting.isCoinSavingEnabled())
                .returns(coinBoxAccount.getAccountId(), setting -> setting.getAccountId()));

        // and: 해지 예시가 비우기 예시의 상태를 소모하지 않도록 별도 저금통을 사용합니다.
        var terminationCoinBoxAccount = accountRepository
                .findByCustomerIdAndAccountNumber(
                        TERMINATE_COINBOX_CUSTOMER_ID,
                        TERMINATION_COINBOX_ACCOUNT_NUMBER
                )
                .orElseThrow();
        assertThat(terminationCoinBoxAccount)
                .returns(ProductType.COINBOX, account -> account.getProductType())
                .returns(AccountStatus.ACTIVE, account -> account.getAccountStatus())
                .returns(35_270L, account -> account.getBalance());

        // and: 개설 정책과 동전모으기의 전일 잔액이 준비된다.
        assertThat(productRepository.findByProductType(ProductType.COINBOX)).isPresent();
        assertThat(productRepository.findByProductType(ProductType.BUSINESS_DEMAND_DEPOSIT)).isPresent();
        assertThat(accountRepository.findEligibleDemandDepositAccounts(ELIGIBLE_ACCOUNT_CUSTOMER_ID))
                .singleElement()
                .returns(ProductType.DEMAND_DEPOSIT, account -> account.getProductType())
                .returns("3333000000003", account -> account.getAccountNumber());
        assertThat(coinBoxPolicyRepository.findAll()).singleElement()
                .returns(100_000L, policy -> policy.getMaxAmount());
        assertThat(accountDailyBalanceRepository.findAll()).singleElement()
                .returns(253_400L, balance -> balance.getClosingBalance());
    }
}
