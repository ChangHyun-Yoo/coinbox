package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.accountdailybalance.entity.AccountDailyBalance;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@DisplayName("일별 최종 잔액 Repository")
class AccountDailyBalanceRepositoryTest extends MySqlTestContainer {

    @Autowired
    private AccountDailyBalanceRepository accountDailyBalanceRepository;

    @Test
    @DisplayName("같은 계좌와 기준일의 잔액을 중복 저장할 수 없다")
    void rejectsDuplicatedAccountAndBalanceDate() {
        // given: PK만 다르고 계좌와 기준일이 같은 두 스냅샷을 준비한다.
        LocalDate balanceDate = LocalDate.of(2026, 8, 26);
        AccountDailyBalance first = AccountDailyBalance.create(60L, 10L, balanceDate, 245_670L);
        AccountDailyBalance second = AccountDailyBalance.create(61L, 10L, balanceDate, 250_000L);
        accountDailyBalanceRepository.saveAndFlush(first);

        // when & then: account_id와 balance_date 복합 UK가 중복 저장을 거부한다.
        assertThatThrownBy(() -> accountDailyBalanceRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
