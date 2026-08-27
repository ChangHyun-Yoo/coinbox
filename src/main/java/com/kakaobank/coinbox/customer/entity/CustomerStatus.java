package com.kakaobank.coinbox.customer.entity;

import lombok.Getter;

@Getter
public enum CustomerStatus {
    ACTIVE("정상"),
    RESTRICTED("이용 제한"),
    WITHDRAWN("탈퇴");

    private final String description;

    CustomerStatus(String description) {
        this.description = description;
    }
}
