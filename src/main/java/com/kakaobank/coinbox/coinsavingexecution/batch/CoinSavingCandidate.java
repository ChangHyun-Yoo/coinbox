package com.kakaobank.coinbox.coinsavingexecution.batch;

import java.time.LocalDate;

/**
 * 잠금 없는 페이징 조회가 전달하는 1차 후보이며 실제 처리는 잠금 후 값을 다시 검증한다.
 */
public record CoinSavingCandidate(
        Long coinBoxId,
        Long coinBoxAccountId,
        Long parentAccountId,
        Long previousClosingBalance,
        LocalDate executionDate
) {
}
