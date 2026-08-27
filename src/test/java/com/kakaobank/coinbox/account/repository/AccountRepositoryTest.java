package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@DisplayName("계좌 Repository")
class AccountRepositoryTest extends MySqlTestContainer {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("상품 유형과 개설일을 포함한 계좌를 저장한다")
    void savesAccountWithProductTypeAndOpenDate() {
        // given: 잔액이 있는 정상 입출금계좌를 준비한다.
        Account account = Account.create(
                10L,
                1L,
                ProductType.DEMAND_DEPOSIT,
                "3333123456789",
                null,
                250_000L,
                LocalDate.of(2026, 8, 27)
        );

        // when: 계좌를 저장하고 다시 조회한다.
        accountRepository.saveAndFlush(account);
        Account foundAccount = accountRepository.findById(10L).orElseThrow();

        // then: 현재 상품 유형, 상태와 개설일이 유지된다.
        assertThat(foundAccount)
                .returns(ProductType.DEMAND_DEPOSIT, Account::getProductType)
                .returns(AccountStatus.ACTIVE, Account::getAccountStatus)
                .returns(LocalDate.of(2026, 8, 27), Account::getAccountOpenDate)
                .returns(250_000L, Account::getBalance);
    }

    @Test
    @DisplayName("같은 계좌번호를 중복 저장할 수 없다")
    void rejectsDuplicatedAccountNumber() {
        // given: 고객은 다르지만 계좌번호가 같은 두 계좌를 준비한다.
        Account first = Account.create(
                11L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000001", null, 0L,
                LocalDate.of(2026, 8, 27)
        );
        Account second = Account.create(
                12L, 2L, ProductType.DEMAND_DEPOSIT, "3333000000001", null, 0L,
                LocalDate.of(2026, 8, 27)
        );
        accountRepository.saveAndFlush(first);

        // when & then: DB의 account_number UK가 중복 저장을 거부한다.
        assertThatThrownBy(() -> accountRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
