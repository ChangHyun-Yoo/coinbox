package com.kakaobank.coinbox.coinsavingexecution.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한 저금통의 일별 동전모으기 처리 결과를 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum CoinSavingExecutionStatus {
    SUCCESS("성공"),
    FAILED("실패"),
    SKIPPED("건너뜀");

    private final String description;
}
