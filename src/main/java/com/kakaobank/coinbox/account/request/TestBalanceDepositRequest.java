package com.kakaobank.coinbox.account.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 로컬 테스트에서 잔고를 증가시킬 계좌와 금액을 지정하는 요청이다.
 */
@Schema(description = "로컬 테스트 전용 계좌 잔고 충전 요청")
public record TestBalanceDepositRequest(
        @Schema(description = "하이픈 없는 13자리 입출금계좌 번호", example = "3333000000003")
        @NotNull
        @Pattern(regexp = "^[0-9]{13}$")
        String accountNumber,
        @Schema(description = "잔고에 더할 금액(원)", example = "10000", minimum = "1")
        @NotNull
        @Positive
        Long amount
) {
}
