package com.kakaobank.coinbox.financialtransaction.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 금융 이벤트의 자금 이동 유형을 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum TransactionType {
    TRANSFER("이체");

    private final String description;
}
