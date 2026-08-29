package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("동전모으기 금액 계산")
class CoinSavingAmountCalculatorTest {

    private final CoinSavingAmountCalculator calculator = new CoinSavingAmountCalculator();

    @Test
    @DisplayName("전일 잔돈과 한도 잔여액 중 작은 금액을 저축한다")
    void limitsSavingAmountByRemainingCoinBoxLimit() {
        // given: 전일 잔돈은 850원이고 저금통 한도는 300원 남았다.

        // when: 실제 저축 금액을 계산한다.
        CoinSavingCalculation result = calculator.calculate(1_282_850L, 10_000L, 99_700L, 100_000L);

        // then: 한도를 넘지 않는 300원만 저축한다.
        assertThat(result.transferable()).isTrue();
        assertThat(result.savingAmount()).isEqualTo(300L);
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    @DisplayName("전일 잔액이 없으면 일별 잔액 없음으로 건너뛴다")
    void skipsWhenDailyBalanceIsMissing() {
        assertSkipped(calculator.calculate(null, 10_000L, 0L, 100_000L),
                CoinSavingReasonCode.DAILY_BALANCE_NOT_FOUND);
    }

    @Test
    @DisplayName("전일 잔돈이 0원이면 저축 금액 없음으로 건너뛴다")
    void skipsWhenRemainderIsZero() {
        assertSkipped(calculator.calculate(15_000L, 10_000L, 0L, 100_000L),
                CoinSavingReasonCode.NO_SAVING_AMOUNT);
    }

    @Test
    @DisplayName("실행 시점 근거계좌 잔액이 1천원 이하면 건너뛴다")
    void skipsWhenSourceBalanceIsOneThousandOrLess() {
        assertSkipped(calculator.calculate(15_850L, 1_000L, 0L, 100_000L),
                CoinSavingReasonCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("저금통 잔액이 최대 한도와 같으면 한도 도달로 건너뛴다")
    void skipsWhenCoinBoxLimitIsReached() {
        assertSkipped(calculator.calculate(15_850L, 10_000L, 100_000L, 100_000L),
                CoinSavingReasonCode.COINBOX_LIMIT_REACHED);
    }

    @Test
    @DisplayName("저금통 잔액이 이미 최대 한도를 초과하면 별도 사유로 건너뛴다")
    void skipsWhenCoinBoxLimitIsAlreadyExceeded() {
        assertSkipped(calculator.calculate(15_850L, 10_000L, 100_001L, 100_000L),
                CoinSavingReasonCode.COINBOX_LIMIT_ALREADY_EXCEEDED);
    }

    private void assertSkipped(CoinSavingCalculation result, CoinSavingReasonCode reasonCode) {
        assertThat(result.transferable()).isFalse();
        assertThat(result.savingAmount()).isZero();
        assertThat(result.reasonCode()).isEqualTo(reasonCode);
    }
}
