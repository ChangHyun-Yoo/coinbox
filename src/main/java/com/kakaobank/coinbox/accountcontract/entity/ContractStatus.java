package com.kakaobank.coinbox.accountcontract.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계좌 계약의 현재 효력과 종료 여부를 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {
    ACTIVE("계약 중"),
    TERMINATED("계약 종료");

    private final String description;
}
