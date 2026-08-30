package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.response.TestBalanceWithdrawalResponse;
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

@DisplayName("테스트 잔고 출금 Service")
class TestBalanceWithdrawalServiceTest {

    private AccountRepository accountRepository;
    private TestBalanceWithdrawalService testBalanceWithdrawalService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        testBalanceWithdrawalService = new TestBalanceWithdrawalService(accountRepository);
    }

    @Test
    @DisplayName("입출금계좌의 잔고를 지정 금액만큼 감소시킨다")
    void decreasesDemandDepositBalance() {
        // given: 출금 전·후 잔고를 가진 같은 입출금계좌 조회 결과를 준비한다.
        Account before = account(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", 100_000L);
        Account after = account(10L, ProductType.DEMAND_DEPOSIT, "3333000000010", 90_000L);
        when(accountRepository.findByAccountNumber("3333000000010"))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(accountRepository.decreaseBalance("3333000000010", 10_000L)).thenReturn(1);

        // when: 테스트 잔고 출금을 요청한다.
        TestBalanceWithdrawalResponse response = testBalanceWithdrawalService.withdraw(
                "3333000000010",
                10_000L
        );

        // then: 단순 UPDATE를 한 번 실행하고 변경 후 잔고를 반환한다.
        assertThat(response)
                .returns("3333000000010", TestBalanceWithdrawalResponse::accountNumber)
                .returns(10_000L, TestBalanceWithdrawalResponse::withdrawnAmount)
                .returns(90_000L, TestBalanceWithdrawalResponse::balanceAfter);
        verify(accountRepository).decreaseBalance("3333000000010", 10_000L);
    }

    @Test
    @DisplayName("입출금계좌가 아니면 잔고를 감소시키지 않는다")
    void rejectsNonDemandDepositAccount() {
        // given: 같은 계좌번호의 상품 유형이 저금통입니다.
        Account coinBox = account(20L, ProductType.COINBOX, "3310000000020", 20_000L);
        when(accountRepository.findByAccountNumber("3310000000020")).thenReturn(Optional.of(coinBox));

        // when & then: 전용 오류를 반환하고 UPDATE를 실행하지 않는다.
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> testBalanceWithdrawalService.withdraw("3310000000020", 10_000L)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEST_BALANCE_WITHDRAWAL_NOT_ALLOWED);
        verify(accountRepository, never()).decreaseBalance("3310000000020", 10_000L);
    }

    @Test
    @DisplayName("출금 금액이 잔고보다 크면 잔고를 감소시키지 않는다")
    void rejectsInsufficientBalance() {
        // given: 출금 요청 금액보다 잔고가 적은 입출금계좌를 준비한다.
        Account account = account(30L, ProductType.DEMAND_DEPOSIT, "3333000000030", 5_000L);
        when(accountRepository.findByAccountNumber("3333000000030")).thenReturn(Optional.of(account));

        // when & then: 잔고 부족 오류를 반환하고 UPDATE를 실행하지 않는다.
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> testBalanceWithdrawalService.withdraw("3333000000030", 10_000L)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        verify(accountRepository, never()).decreaseBalance("3333000000030", 10_000L);
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
