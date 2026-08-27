package com.kakaobank.coinbox.financialtransaction.entity;

import lombok.Getter;

@Getter
public enum TransactionType {
    TRANSFER("이체");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }
}
