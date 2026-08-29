package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountdailybalance.batch.AccountBalanceSnapshot;
import com.kakaobank.coinbox.accountdailybalance.batch.DailyBalanceInsertRow;
import com.kakaobank.coinbox.accountdailybalance.entity.AccountDailyBalance;
import com.kakaobank.coinbox.accountdailybalance.repository.AccountDailyBalanceRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({MySqlTestContainer.class, DailyBalanceJdbcRepository.class})
@DisplayName("일별 최종 잔액 Repository")
class DailyBalanceRepositoryTest {

    @Autowired
    private DailyBalanceQueryRepository queryRepository;

    @Autowired
    private DailyBalanceJdbcRepository jdbcRepository;

    @Autowired
    private AccountDailyBalanceRepository accountDailyBalanceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("ACTIVE와 RESTRICTED 계좌만 ID 오름차순으로 조회한다")
    void findsActiveAndRestrictedSnapshotTargets() {
        // given: 정상, 거래 제한과 해지 계좌를 저장한다.
        Account active = account(30L, "3333000000030", 3_000L);
        Account restricted = account(10L, "3333000000010", 1_000L);
        Account closed = account(20L, "3333000000020", 0L);
        closed.close();
        accountRepository.saveAllAndFlush(List.of(active, restricted, closed));
        jdbcTemplate.update("update account set account_status = 'RESTRICTED' where account_id = ?", 10L);
        entityManager.clear();

        // when: 일별 잔액 대상을 조회한다.
        List<AccountBalanceSnapshot> result = queryRepository.findSnapshotTargets();

        // then: CLOSED를 제외한 두 계좌가 ID 오름차순으로 반환된다.
        assertThat(result)
                .extracting(AccountBalanceSnapshot::accountId)
                .containsExactly(10L, 30L);
    }

    @Test
    @DisplayName("같은 계좌와 기준일은 재실행해도 기존 잔액을 덮어쓰지 않는다")
    void batchInsertsOnlyMissingDailyBalances() {
        // given: 두 계좌의 전일 잔액 저장 행을 준비한다.
        LocalDate balanceDate = LocalDate.of(2026, 8, 28);
        LocalDateTime auditedAt = LocalDateTime.of(2026, 8, 29, 0, 0);
        List<DailyBalanceInsertRow> firstRows = List.of(
                row(100L, 10L, balanceDate, 1_500L, auditedAt),
                row(101L, 20L, balanceDate, 2_500L, auditedAt)
        );

        // when: 같은 기준일을 다른 잔액으로 다시 저장한다.
        int firstInserted = jdbcRepository.batchInsertIfAbsent(firstRows);
        int secondInserted = jdbcRepository.batchInsertIfAbsent(List.of(
                row(200L, 10L, balanceDate, 9_999L, auditedAt.plusHours(1)),
                row(201L, 20L, balanceDate, 9_999L, auditedAt.plusHours(1))
        ));
        entityManager.clear();

        // then: 최초 두 건만 생성되고 성공 당시 잔액이 보존된다.
        assertThat(firstInserted).isEqualTo(2);
        assertThat(secondInserted).isZero();
        assertThat(accountDailyBalanceRepository.findAll())
                .extracting(AccountDailyBalance::getClosingBalance)
                .containsExactlyInAnyOrder(1_500L, 2_500L);
    }

    private Account account(Long accountId, String accountNumber, Long balance) {
        return Account.create(
                accountId,
                1L,
                ProductType.DEMAND_DEPOSIT,
                accountNumber,
                null,
                balance,
                LocalDate.of(2026, 8, 1)
        );
    }

    private DailyBalanceInsertRow row(
            Long id,
            Long accountId,
            LocalDate balanceDate,
            Long balance,
            LocalDateTime auditedAt
    ) {
        return new DailyBalanceInsertRow(id, accountId, balanceDate, balance, auditedAt, auditedAt);
    }
}
