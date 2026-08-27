package com.kakaobank.coinbox.financialtransaction.entity;

import lombok.Getter;

@Getter
public enum TransactionStatus {
    PENDING("처리 대기"),
    SUCCESS("성공"),
    FAILED("실패");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }
}
