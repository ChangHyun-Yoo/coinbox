package com.kakaobank.coinbox.coinsavingexecution.entity;

import lombok.Getter;

@Getter
public enum CoinSavingExecutionStatus {
    SUCCESS("성공"),
    FAILED("실패"),
    SKIPPED("건너뜀");

    private final String description;

    CoinSavingExecutionStatus(String description) {
        this.description = description;
    }
}
