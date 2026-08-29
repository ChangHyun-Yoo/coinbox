package com.kakaobank.coinbox.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계좌가 현재 거래 가능한지와 해지 여부를 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus {
    ACTIVE("정상"),
    RESTRICTED("거래 제한"),
    CLOSED("해지");

    private final String description;
}
