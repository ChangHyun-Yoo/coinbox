package com.kakaobank.coinbox.coinbox.response;

import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.accountcontract.entity.ContractStatus;
import com.kakaobank.coinbox.coinbox.service.CoinBoxTerminationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 잔액 이전 결과와 저금통 계좌·계약의 최종 종료 상태를 반환한다.
 */
@Schema(description = "저금통 해지 결과")
public record TerminateCoinBoxResponse(
        @Schema(description = "해지된 저금통 계좌 ID", example = "710000000000000101")
        String accountId,
        @Schema(description = "하이픈 없는 13자리 저금통 계좌번호", example = "3310000012345", pattern = "^3310[0-9]{9}$")
        String accountNumber,
        @Schema(description = "해지 후 계좌 상태", example = "CLOSED")
        AccountStatus accountStatus,
        @Schema(description = "해지 후 계약 상태", example = "TERMINATED")
        ContractStatus contractStatus,
        @Schema(description = "해지 전 근거계좌로 이전한 잔액. 잔액이 없으면 0", example = "4360", minimum = "0")
        Long transferredAmount,
        @Schema(description = "해지일", example = "2026-08-27", format = "date")
        LocalDate terminationDate
) {

    /**
     * 해지 서비스 결과를 외부 API 응답으로 변환한다.
     */
    public static TerminateCoinBoxResponse from(CoinBoxTerminationResult result) {
        return new TerminateCoinBoxResponse(
                String.valueOf(result.account().getAccountId()),
                result.account().getAccountNumber(),
                result.account().getAccountStatus(),
                result.accountContract().getContractStatus(),
                result.transferredAmount(),
                result.terminationDate()
        );
    }
}
