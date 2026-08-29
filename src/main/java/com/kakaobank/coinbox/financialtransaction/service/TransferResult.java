package com.kakaobank.coinbox.financialtransaction.service;

/**
 * 당행 이체가 Commit 대상으로 만든 거래 ID, 이체 금액과 두 계좌의 변경 후 잔액이다.
 */
public record TransferResult(
        Long transactionId,
        Long amount,
        Long sourceBalanceAfter,
        Long targetBalanceAfter
) {
}
