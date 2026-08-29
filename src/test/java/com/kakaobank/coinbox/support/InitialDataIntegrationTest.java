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
    private static final Long NEW_COINBOX_CUSTOMER_ID = 700000000000000002L;
    private static final String COINBOX_ACCOUNT_NUMBER = "3310000000001";

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

        // then: 기존 저금통 고객과 신규 가입 고객이 함께 준비된다.
        assertThat(customerRepository.findById(EXISTING_COINBOX_CUSTOMER_ID)).isPresent();
        assertThat(customerRepository.findById(NEW_COINBOX_CUSTOMER_ID)).isPresent();

        // and: 비우기·해지용 ACTIVE 저금통과 연결 설정이 생성된다.
        var coinBoxAccount = accountRepository
                .findByCustomerIdAndAccountNumber(EXISTING_COINBOX_CUSTOMER_ID, COINBOX_ACCOUNT_NUMBER)
                .orElseThrow();
        assertThat(coinBoxAccount.getProductType()).isEqualTo(ProductType.COINBOX);
        assertThat(coinBoxAccount.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(coinBoxRepository.findAll()).singleElement()
                .returns(true, coinBox -> coinBox.isCoinSavingEnabled())
                .returns(coinBoxAccount.getAccountId(), coinBox -> coinBox.getAccountId());

        // and: 개설 정책과 동전모으기의 전일 잔액이 준비된다.
        assertThat(productRepository.findByProductType(ProductType.COINBOX)).isPresent();
        assertThat(coinBoxPolicyRepository.findAll()).singleElement()
                .returns(100_000L, policy -> policy.getMaxAmount());
        assertThat(accountDailyBalanceRepository.findAll()).singleElement()
                .returns(253_400L, balance -> balance.getClosingBalance());
    }
}
