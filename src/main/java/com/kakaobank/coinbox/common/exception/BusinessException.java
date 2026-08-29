package com.kakaobank.coinbox.common.exception;

import lombok.Getter;

/**
 * 서비스에서 감지한 업무 오류를 공통 ErrorCode와 함께 전달한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 오류 코드의 사용자 메시지를 예외 메시지로 함께 설정한다.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
