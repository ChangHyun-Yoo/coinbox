package com.kakaobank.coinbox.account.entity;

import lombok.Getter;

@Getter
public enum AccountStatus {
    ACTIVE("정상"),
    RESTRICTED("거래 제한"),
    CLOSED("해지");

    private final String description;

    AccountStatus(String description) {
        this.description = description;
    }
}
