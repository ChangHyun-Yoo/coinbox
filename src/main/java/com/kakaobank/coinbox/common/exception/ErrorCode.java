package com.kakaobank.coinbox.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-001", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-002", "서버 내부 오류가 발생했습니다."),

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER-001", "고객을 찾을 수 없습니다."),
    CUSTOMER_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "CUSTOMER-002", "저금통 가입이 가능한 개인 고객이 아닙니다."),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT-001", "계좌를 찾을 수 없습니다."),
    ELIGIBLE_DEMAND_DEPOSIT_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ACCOUNT-002",
            "저금통 가입이 가능한 입출금 계좌가 없습니다."
    ),
    ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "ACCOUNT-003", "활성 상태의 계좌가 아닙니다."),
    MEETING_ACCOUNT_NOT_ALLOWED(HttpStatus.CONFLICT, "ACCOUNT-004", "모임통장 계좌로는 저금통에 가입할 수 없습니다."),
    INSUFFICIENT_ACCOUNT_BALANCE(HttpStatus.CONFLICT, "ACCOUNT-005", "계좌 잔액이 부족합니다."),

    ACCOUNT_CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT-001", "계좌 계약을 찾을 수 없습니다."),

    COINBOX_NOT_FOUND(HttpStatus.NOT_FOUND, "COINBOX-001", "저금통을 찾을 수 없습니다."),
    COINBOX_ALREADY_EXISTS(HttpStatus.CONFLICT, "COINBOX-002", "고객은 저금통을 한 개만 가입할 수 있습니다."),
    COINBOX_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "COINBOX-003", "저금통 최대 보유 한도를 초과합니다."),
    COINBOX_BALANCE_EMPTY(HttpStatus.CONFLICT, "COINBOX-004", "저금통에 비울 잔액이 없습니다."),

    ACCOUNT_DAILY_BALANCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "DAILY-BALANCE-001",
            "계좌의 일별 최종 잔액을 찾을 수 없습니다."
    ),

    COIN_SAVING_NOT_ENABLED(HttpStatus.CONFLICT, "COIN-SAVING-001", "동전모으기가 활성화되어 있지 않습니다."),
    COIN_SAVING_EXECUTION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "COIN-SAVING-002",
            "해당 일자의 동전모으기 실행 이력이 이미 존재합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
