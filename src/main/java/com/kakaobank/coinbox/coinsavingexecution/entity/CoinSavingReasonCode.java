package com.kakaobank.coinbox.coinsavingexecution.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 동전모으기가 자금 이동 없이 종료되거나 실패한 이유를 실행 이력에 기록한다.
 */
@Getter
@RequiredArgsConstructor
public enum CoinSavingReasonCode {
    NO_SAVING_AMOUNT("저축 금액 없음"),
    INSUFFICIENT_BALANCE("잔액 부족"),
    COINBOX_LIMIT_REACHED("저금통 한도 도달"),
    COINBOX_LIMIT_ALREADY_EXCEEDED("저금통 한도 초과"),
    DAILY_BALANCE_NOT_FOUND("일별 잔액 없음"),
    ACCOUNT_NOT_ACTIVE("계좌 비정상"),
    SYSTEM_ERROR("시스템 오류");

    private final String description;
}
