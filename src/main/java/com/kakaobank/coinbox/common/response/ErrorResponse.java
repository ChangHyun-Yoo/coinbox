package com.kakaobank.coinbox.common.response;

import com.kakaobank.coinbox.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 모든 온라인 API에서 공통으로 사용하는 오류 응답 형식이다.
 */
@Schema(description = "모든 온라인 API가 공통으로 사용하는 오류 응답")
public record ErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "409")
        int status,
        @Schema(description = "Client 분기용 업무 오류 코드", example = "COINBOX_ALREADY_EXISTS")
        String code,
        @Schema(description = "사용자에게 표시 가능한 한글 오류 메시지", example = "이미 이용 중인 저금통이 있습니다.")
        String message,
        @Schema(description = "오류 응답 생성 일시", example = "2026-08-30T10:15:30", format = "date-time")
        LocalDateTime timestamp
) {

    /**
     * ErrorCode의 계약을 현재 시각의 오류 응답으로 변환한다.
     */
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now()
        );
    }
}
