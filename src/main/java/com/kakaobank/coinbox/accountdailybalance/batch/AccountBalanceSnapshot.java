package com.kakaobank.coinbox.accountdailybalance.batch;

/**
 * 일별 잔액 배치가 계좌에서 읽어 온 식별자와 현재 잔액의 불변 스냅샷이다.
 */
public record AccountBalanceSnapshot(
        Long accountId,
        Long balance
) {
}
