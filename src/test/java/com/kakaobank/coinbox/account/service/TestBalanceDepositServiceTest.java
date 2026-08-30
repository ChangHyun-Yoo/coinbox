package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.response.TestBalanceDepositResponse;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("테스트 잔고 충전 Service")
class TestBalanceDepositServiceTest {

    private AccountRepository accountRepository;
    private TestBalanceDepositService testBalanceDepositService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        testBalanceDepositService = new TestBalanceDepositService(accountRepository);
    }

    @Test
    @DisplayName("입출금계좌의 잔고를 지정 금액만큼 증가시킨다")
    void increasesDemandDepositBalance() {
        // given: 충전 전·후 잔고를 가진 같은 입출금계좌 조회 결과를 준비한다.
        Account before = account(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", 100_000L);
        Account after = account(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", 110_000L);
        when(accountRepository.findByAccountNumber("3333000000010"))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(accountRepository.increaseBalance("3333000000010", 10_000L)).thenReturn(1);

        // when: 테스트 잔고 충전을 요청한다.
        TestBalanceDepositResponse response = testBalanceDepositService.deposit(
                "3333000000010",
                10_000L
        );

        // then: 단순 UPDATE를 한 번 실행하고 변경 후 잔고를 반환한다.
        assertThat(response)
                .returns("3333000000010", TestBalanceDepositResponse::accountNumber)
                .returns(10_000L, TestBalanceDepositResponse::depositedAmount)
                .returns(110_000L, TestBalanceDepositResponse::balanceAfter);
        verify(accountRepository).increaseBalance("3333000000010", 10_000L);
    }

    @Test
    @DisplayName("입출금계좌가 아니면 잔고를 증가시키지 않는다")
    void rejectsNonDemandDepositAccount() {
        // given: 같은 계좌번호의 상품 유형이 저금통입니다.
        Account coinBox = account(20L, ProductType.COINBOX, "3310000000020", 20_000L);
        when(accountRepository.findByAccountNumber("3310000000020")).thenReturn(Optional.of(coinBox));

        // when & then: 전용 오류를 반환하고 UPDATE를 실행하지 않는다.
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> testBalanceDepositService.deposit("3310000000020", 10_000L)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEST_BALANCE_DEPOSIT_NOT_ALLOWED);
        verify(accountRepository, never()).increaseBalance("3310000000020", 10_000L);
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
