package com.kakaobank.coinbox.account.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 테스트 잔고 출금 대상과 감소 금액 및 변경 후 잔고를 반환한다.
 */
@Schema(description = "로컬 테스트 전용 계좌 잔고 출금 결과")
public record TestBalanceWithdrawalResponse(
        @Schema(description = "잔고를 감소시킨 입출금계좌 번호", example = "3333000000004")
        String accountNumber,
        @Schema(description = "잔고에서 뺀 금액(원)", example = "5000", minimum = "1")
        Long withdrawnAmount,
        @Schema(description = "변경 후 계좌 잔고(원)", example = "181420", minimum = "0")
        Long balanceAfter
) {
}
