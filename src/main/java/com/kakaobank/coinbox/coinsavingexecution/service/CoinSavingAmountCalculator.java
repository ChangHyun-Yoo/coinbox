package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DB 접근 없이 전일 잔돈, 실행 시점 잔액과 저금통 한도로 실제 저축 금액을 결정한다.
 */
@Component
@Slf4j
public class CoinSavingAmountCalculator {

    private static final long COIN_UNIT = 1_000L;

    /**
     * 전일 천 원 미만 잔돈과 실행 시점 잔액·한도를 반영한 저축 결과를 계산한다.
     */
    public CoinSavingCalculation calculate(
            Long previousClosingBalance,
            Long currentSourceBalance,
            Long currentCoinBoxBalance,
            Long maxAmount
    ) {
        validateCurrentBalances(currentSourceBalance, currentCoinBoxBalance, maxAmount);
        if (previousClosingBalance == null) {
            return CoinSavingCalculation.skipped(CoinSavingReasonCode.DAILY_BALANCE_NOT_FOUND);
        }
        if (previousClosingBalance < 0L) {
            throw new IllegalArgumentException("previousClosingBalance must not be negative");
        }

        long plannedAmount = previousClosingBalance % COIN_UNIT;
        if (plannedAmount == 0L) {
            return CoinSavingCalculation.skipped(CoinSavingReasonCode.NO_SAVING_AMOUNT);
        }
        if (currentSourceBalance <= COIN_UNIT) {
            return CoinSavingCalculation.skipped(CoinSavingReasonCode.INSUFFICIENT_BALANCE);
        }
        if (currentCoinBoxBalance > maxAmount) {
            return CoinSavingCalculation.skipped(CoinSavingReasonCode.COINBOX_LIMIT_ALREADY_EXCEEDED);
        }
        if (currentCoinBoxBalance.equals(maxAmount)) {
            return CoinSavingCalculation.skipped(CoinSavingReasonCode.COINBOX_LIMIT_REACHED);
        }

        long remainingLimit = maxAmount - currentCoinBoxBalance;
        return CoinSavingCalculation.transferable(Math.min(plannedAmount, remainingLimit));
    }

    /**
     * 계산에 사용하는 현재 잔액과 정책 한도의 기본 범위를 검증한다.
     */
    private void validateCurrentBalances(Long sourceBalance, Long coinBoxBalance, Long maxAmount) {
        if (sourceBalance == null || sourceBalance < 0L) {
            throw new IllegalArgumentException("currentSourceBalance must not be negative");
        }
        if (coinBoxBalance == null || coinBoxBalance < 0L) {
            throw new IllegalArgumentException("currentCoinBoxBalance must not be negative");
        }
        if (maxAmount == null || maxAmount <= 0L) {
            throw new IllegalArgumentException("maxAmount must be positive");
        }
    }
}
