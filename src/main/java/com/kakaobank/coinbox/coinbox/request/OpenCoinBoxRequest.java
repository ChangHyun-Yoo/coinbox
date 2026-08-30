package com.kakaobank.coinbox.coinbox.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 저금통 개설 시 고객이 선택한 근거계좌를 전달한다.
 */
@Schema(description = "저금통 개설 요청")
public record OpenCoinBoxRequest(
        @Schema(
                description = "저금통의 근거계좌로 선택한 입출금계좌 ID",
                type = "string",
                example = "710000000000000005",
                pattern = "^[1-9][0-9]*$"
        )
        @NotNull @Positive Long parentAccountId
) {
}
