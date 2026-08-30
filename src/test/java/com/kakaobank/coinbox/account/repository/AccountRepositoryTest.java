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
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("계좌 Repository")
class AccountRepositoryTest {

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

    @Test
    @DisplayName("계좌번호가 이미 사용 중인지 확인한다")
    void checksWhetherAccountNumberAlreadyExists() {
        // given: 13자리 입출금계좌 번호를 저장한다.
        Account account = Account.create(
                13L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000002", null, 0L,
                LocalDate.of(2026, 8, 28)
        );
        accountRepository.saveAndFlush(account);

        // when & then: 저장된 번호만 사용 중인 것으로 판단한다.
        assertThat(accountRepository.countByAccountNumber("3333000000002")).isOne();
        assertThat(accountRepository.countByAccountNumber("3333000000003")).isZero();
    }

    @Test
    @DisplayName("입출금계좌의 잔고만 지정 금액만큼 증가시킨다")
    void increasesOnlyDemandDepositAccountBalance() {
        // given: 입출금계좌와 저금통 계좌를 준비한다.
        Account demandDeposit = Account.create(
                14L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000014", null, 100_000L,
                LocalDate.of(2026, 8, 28)
        );
        Account coinBox = Account.create(
                15L, 1L, ProductType.COINBOX, "3310000000015", 14L, 20_000L,
                LocalDate.of(2026, 8, 28)
        );
        accountRepository.saveAllAndFlush(List.of(demandDeposit, coinBox));

        // when: 같은 금액으로 두 계좌의 테스트 잔고 증가를 요청한다.
        int demandDepositUpdated = accountRepository.increaseBalance("3333000000014", 10_000L);
        int coinBoxUpdated = accountRepository.increaseBalance("3310000000015", 10_000L);

        // then: 입출금계좌만 UPDATE되고 저금통 잔고는 변경되지 않는다.
        assertThat(demandDepositUpdated).isOne();
        assertThat(coinBoxUpdated).isZero();
        assertThat(accountRepository.findById(14L).orElseThrow().getBalance()).isEqualTo(110_000L);
        assertThat(accountRepository.findById(15L).orElseThrow().getBalance()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("입출금계좌에서 잔고 이하의 금액만 감소시킨다")
    void decreasesOnlyAvailableDemandDepositBalance() {
        // given: 입출금계좌와 저금통 계좌를 준비한다.
        Account demandDeposit = Account.create(
                16L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000016", null, 100_000L,
                LocalDate.of(2026, 8, 28)
        );
        Account coinBox = Account.create(
                17L, 1L, ProductType.COINBOX, "3310000000017", 16L, 20_000L,
                LocalDate.of(2026, 8, 28)
        );
        accountRepository.saveAllAndFlush(List.of(demandDeposit, coinBox));

        // when: 정상 출금, 저금통 출금과 잔고 초과 출금을 차례로 요청한다.
        int demandDepositUpdated = accountRepository.decreaseBalance("3333000000016", 10_000L);
        int coinBoxUpdated = accountRepository.decreaseBalance("3310000000017", 10_000L);
        int insufficientUpdated = accountRepository.decreaseBalance("3333000000016", 100_000L);

        // then: 입출금계좌의 출금 가능 금액만 UPDATE되고 음수 잔고를 만들지 않는다.
        assertThat(demandDepositUpdated).isOne();
        assertThat(coinBoxUpdated).isZero();
        assertThat(insufficientUpdated).isZero();
        assertThat(accountRepository.findById(16L).orElseThrow().getBalance()).isEqualTo(90_000L);
        assertThat(accountRepository.findById(17L).orElseThrow().getBalance()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("이체 계좌를 ID 오름차순으로 비관적 잠금 조회한다")
    void locksTransferAccountsInAscendingIdOrder() {
        // given: ID 순서와 반대로 저장한 두 정상 계좌를 준비한다.
        Account higherIdAccount = Account.create(
                22L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000022", null, 10_000L,
                LocalDate.of(2026, 8, 28)
        );
        Account lowerIdAccount = Account.create(
                21L, 1L, ProductType.DEMAND_DEPOSIT, "3333000000021", null, 20_000L,
                LocalDate.of(2026, 8, 28)
        );
        accountRepository.saveAllAndFlush(List.of(higherIdAccount, lowerIdAccount));

        // when: 전달 순서와 무관하게 두 계좌를 잠금 조회한다.
        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdateOrderByAccountId(List.of(22L, 21L));

        // then: DB가 account_id 오름차순으로 잠금을 획득한 결과를 반환한다.
        assertThat(lockedAccounts)
                .extracting(Account::getAccountId)
                .containsExactly(21L, 22L);
    }
}
