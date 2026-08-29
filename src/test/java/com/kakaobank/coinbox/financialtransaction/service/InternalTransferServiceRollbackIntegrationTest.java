package com.kakaobank.coinbox.financialtransaction.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.accountentry.entity.EntryCode;
import com.kakaobank.coinbox.accountentry.repository.AccountEntryRepository;
import com.kakaobank.coinbox.financialtransaction.repository.FinancialTransactionRepository;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(MySqlTestContainer.class)
@DisplayName("당행 이체 Rollback 통합 테스트")
class InternalTransferServiceRollbackIntegrationTest {

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

    @MockitoBean
    private AccountEntryRepository accountEntryRepository;

    @BeforeEach
    void cleanDatabase() {
        financialTransactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("원장 저장이 실패하면 두 잔액과 금융거래를 모두 Rollback한다")
    void rollsBackEveryChangeWhenEntryPersistenceFails() {
        // given: 정상 계좌를 저장하고 원장 저장 단계에서 장애가 발생하도록 한다.
        Account source = account(100L, ProductType.COINBOX, "3310000000100", 10_000L);
        Account target = account(200L, ProductType.DEMAND_DEPOSIT, "3333000000200", 2_000L);
        accountRepository.saveAllAndFlush(List.of(source, target));
        doThrow(new IllegalStateException("원장 저장 실패"))
                .when(accountEntryRepository)
                .saveAll(anyList());

        // when: 이체 트랜잭션 중 원장 저장이 실패한다.
        assertThatThrownBy(() -> internalTransferService.transfer(100L, 200L, 4_000L, LEDGER_SPEC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("원장 저장 실패");

        // then: 별도 조회에서 두 잔액과 금융거래가 모두 이체 전 상태임을 확인한다.
        assertThat(accountRepository.findById(100L).orElseThrow().getBalance()).isEqualTo(10_000L);
        assertThat(accountRepository.findById(200L).orElseThrow().getBalance()).isEqualTo(2_000L);
        assertThat(financialTransactionRepository.count()).isZero();
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
