package com.kakaobank.coinbox.account.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 로컬 테스트에서 잔고를 감소시킬 계좌와 금액을 지정하는 요청이다.
 */
@Schema(description = "로컬 테스트 전용 계좌 잔고 출금 요청")
public record TestBalanceWithdrawalRequest(
        @Schema(description = "하이픈 없는 13자리 입출금계좌 번호", example = "3333000000004")
        @NotNull
        @Pattern(regexp = "^[0-9]{13}$")
        String accountNumber,
        @Schema(description = "잔고에서 뺄 금액(원)", example = "5000", minimum = "1")
        @NotNull
        @Positive
        Long amount
) {
}
