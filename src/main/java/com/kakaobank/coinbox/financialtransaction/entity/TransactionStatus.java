package com.kakaobank.coinbox.financialtransaction.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 금융거래의 처리 진행 상태와 최종 성공 여부를 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum TransactionStatus {
    PENDING("처리 대기"),
    SUCCESS("성공"),
    FAILED("실패");

    private final String description;
}
