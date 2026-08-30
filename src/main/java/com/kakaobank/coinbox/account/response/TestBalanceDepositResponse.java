package com.kakaobank.coinbox.account.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 테스트 잔고 충전 대상과 증가 금액 및 변경 후 잔고를 반환한다.
 */
@Schema(description = "로컬 테스트 전용 계좌 잔고 충전 결과")
public record TestBalanceDepositResponse(
        @Schema(description = "잔고를 증가시킨 입출금계좌 번호", example = "3333000000003")
        String accountNumber,
        @Schema(description = "잔고에 더한 금액(원)", example = "10000", minimum = "1")
        Long depositedAmount,
        @Schema(description = "변경 후 계좌 잔고(원)", example = "135670", minimum = "0")
        Long balanceAfter
) {
}
