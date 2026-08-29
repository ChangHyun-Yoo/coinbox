package com.kakaobank.coinbox.coinbox.response;

import com.kakaobank.coinbox.financialtransaction.service.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저금통 비우기로 생성된 거래와 두 계좌의 변경 후 잔액을 반환한다.
 */
@Schema(description = "저금통 비우기 결과")
public record EmptyCoinBoxResponse(
        @Schema(description = "생성된 당행 이체 거래 ID", example = "720000000000000001")
        String transactionId,
        @Schema(description = "근거계좌로 이전한 금액(원)", example = "4360", minimum = "1")
        Long amount,
        @Schema(description = "비우기 후 저금통 잔액(원)", example = "0", minimum = "0")
        Long coinBoxBalanceAfter,
        @Schema(description = "비우기 후 근거계좌 잔액(원)", example = "250030", minimum = "0")
        Long parentAccountBalanceAfter
) {

    /**
     * 공통 당행 이체 결과를 저금통 비우기 응답으로 변환한다.
     */
    public static EmptyCoinBoxResponse from(TransferResult result) {
        return new EmptyCoinBoxResponse(
                String.valueOf(result.transactionId()),
                result.amount(),
                result.sourceBalanceAfter(),
                result.targetBalanceAfter()
        );
    }
}
