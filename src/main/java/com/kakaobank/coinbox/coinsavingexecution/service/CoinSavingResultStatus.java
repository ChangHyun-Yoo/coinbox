package com.kakaobank.coinbox.coinsavingexecution.service;

/**
 * 후보 한 건의 처리 결과를 배치 Writer와 테스트에서 명확하게 구분한다.
 */
public enum CoinSavingResultStatus {
    SUCCESS,
    SKIPPED,
    EXCLUDED,
    DUPLICATE
}
