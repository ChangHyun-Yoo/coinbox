package com.kakaobank.coinbox.accountcontract.entity;

import lombok.Getter;

@Getter
public enum ContractStatus {
    ACTIVE("계약 중"),
    TERMINATED("계약 종료");

    private final String description;

    ContractStatus(String description) {
        this.description = description;
    }
}
