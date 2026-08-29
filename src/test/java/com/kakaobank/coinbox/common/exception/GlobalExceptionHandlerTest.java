package com.kakaobank.coinbox.common.exception;

import com.kakaobank.coinbox.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전역 예외 처리기")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("비즈니스 예외를 ErrorCode에 정의된 응답으로 변환한다")
    void handlesBusinessException() {
        // given: 가입 가능한 입출금 계좌가 없는 비즈니스 예외를 준비한다.
        ErrorCode errorCode = ErrorCode.ELIGIBLE_ACCOUNT_NOT_FOUND;
        BusinessException exception = new BusinessException(errorCode);

        // when: 전역 예외 처리기로 비즈니스 예외를 처리한다.
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusinessException(exception);

        // then: ErrorCode의 HTTP 상태와 code, message가 응답에 포함된다.
        assertThat(response.getStatusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(response.getBody())
                .isNotNull()
                .returns(errorCode.getCode(), ErrorResponse::code)
                .returns(errorCode.getMessage(), ErrorResponse::message);
    }

    @Test
    @DisplayName("잘못된 요청 예외를 공통 오류 응답으로 변환한다")
    void handlesInvalidRequestException() {
        // given: 잘못된 요청을 나타내는 예외를 준비한다.
        IllegalArgumentException exception = new IllegalArgumentException("잘못된 입력값");

        // when: 전역 예외 처리기로 잘못된 요청 예외를 처리한다.
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidRequest(exception);

        // then: 잘못된 요청 ErrorCode가 응답에 포함된다.
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_REQUEST.getHttpStatus());
        assertThat(response.getBody())
                .isNotNull()
                .returns(ErrorCode.INVALID_REQUEST.getCode(), ErrorResponse::code)
                .returns(ErrorCode.INVALID_REQUEST.getMessage(), ErrorResponse::message);
    }
}
