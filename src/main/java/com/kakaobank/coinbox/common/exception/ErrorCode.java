package com.kakaobank.coinbox.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 온라인 API가 반환하는 HTTP 상태, 업무 코드와 사용자 메시지를 한곳에서 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류가 발생했습니다."
    ),

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "고객을 찾을 수 없습니다."),
    CUSTOMER_NOT_ELIGIBLE(
            HttpStatus.BAD_REQUEST,
            "CUSTOMER_NOT_ELIGIBLE",
            "저금통 가입이 가능한 고객 상태가 아닙니다."
    ),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),
    ELIGIBLE_ACCOUNT_NOT_FOUND(
            HttpStatus.CONFLICT,
            "ELIGIBLE_ACCOUNT_NOT_FOUND",
            "저금통 가입이 가능한 입출금계좌가 없습니다. 먼저 입출금계좌를 만들어야 합니다."
    ),
    ACCOUNT_NOT_ELIGIBLE(
            HttpStatus.CONFLICT,
            "ACCOUNT_NOT_ELIGIBLE",
            "선택한 계좌는 저금통 가입 조건을 충족하지 않습니다."
    ),
    ACCOUNT_NOT_TRANSFERABLE(
            HttpStatus.CONFLICT,
            "ACCOUNT_NOT_TRANSFERABLE",
            "계좌 거래가 불가능한 상태입니다."
    ),
    TEST_BALANCE_DEPOSIT_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "TEST_BALANCE_DEPOSIT_NOT_ALLOWED",
            "입출금계좌만 테스트 잔고를 증가시킬 수 있습니다."
    ),
    TEST_BALANCE_WITHDRAWAL_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "TEST_BALANCE_WITHDRAWAL_NOT_ALLOWED",
            "입출금계좌만 테스트 잔고를 감소시킬 수 있습니다."
    ),
    INSUFFICIENT_ACCOUNT_BALANCE(
            HttpStatus.CONFLICT,
            "INSUFFICIENT_ACCOUNT_BALANCE",
            "계좌 잔액이 부족합니다."
    ),
    ACCOUNT_NUMBER_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "ACCOUNT_NUMBER_GENERATION_FAILED",
            "사용 가능한 계좌번호를 생성하지 못했습니다."
    ),

    COINBOX_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COINBOX_NOT_FOUND",
            "유효한 저금통 계좌를 찾을 수 없습니다."
    ),
    COINBOX_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "COINBOX_ALREADY_EXISTS",
            "이미 이용 중인 저금통이 있습니다."
    ),
    COINBOX_BALANCE_EMPTY(
            HttpStatus.CONFLICT,
            "COINBOX_BALANCE_EMPTY",
            "저금통이 아직 비어있어요. 조금 더 모인 뒤에 비워주세요."
    ),
    COINBOX_ALREADY_TERMINATED(
            HttpStatus.CONFLICT,
            "COINBOX_ALREADY_TERMINATED",
            "이미 해지된 저금통입니다."
    ),
    COINBOX_INVALID_STATE(
            HttpStatus.CONFLICT,
            "COINBOX_INVALID_STATE",
            "저금통 데이터 상태가 올바르지 않습니다."
    ),
    COINBOX_POLICY_NOT_FOUND(
            HttpStatus.SERVICE_UNAVAILABLE,
            "COINBOX_POLICY_NOT_FOUND",
            "현재 적용 가능한 저금통 상품 정책이 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
