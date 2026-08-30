package com.kakaobank.coinbox.coinbox.response;

import com.kakaobank.coinbox.account.entity.AccountStatus;
import com.kakaobank.coinbox.coinbox.service.CoinBoxOpenResult;
import com.kakaobank.coinbox.product.entity.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 신규 저금통 계좌와 최초 동전모으기 설정을 반환한다.
 */
@Schema(description = "신규 개설된 저금통 계좌와 동전모으기 설정")
public record OpenCoinBoxResponse(
        @Schema(description = "신규 생성되는 저금통 계좌 ID", example = "810000000000000001")
        String accountId,
        @Schema(description = "신규 채번되는 하이픈 없는 13자리 저금통 계좌번호", example = "3310000000003", pattern = "^3310[0-9]{9}$")
        String accountNumber,
        @Schema(description = "현재 상품 유형", example = "COINBOX")
        ProductType productType,
        @Schema(description = "계좌 상태: ACTIVE(정상), RESTRICTED(거래 제한), CLOSED(해지)", example = "ACTIVE")
        AccountStatus accountStatus,
        @Schema(description = "data.sql의 개설 전용 고객에게 연결된 근거계좌 ID", example = "710000000000000005")
        String parentAccountId,
        @Schema(description = "동전모으기 활성 여부. 신규 가입 시 true", example = "true")
        boolean coinSavingEnabled,
        @Schema(description = "동전모으기 시작일", example = "2026-08-30", format = "date")
        LocalDate coinSavingStartDate,
        @Schema(description = "저금통 계좌 개설일", example = "2026-08-30", format = "date")
        LocalDate accountOpenDate
) {

    /**
     * 개설 서비스 결과를 외부 API 응답으로 변환한다.
     */
    public static OpenCoinBoxResponse from(CoinBoxOpenResult result) {
        return new OpenCoinBoxResponse(
                String.valueOf(result.account().getAccountId()),
                result.account().getAccountNumber(),
                result.account().getProductType(),
                result.account().getAccountStatus(),
                String.valueOf(result.account().getParentAccountId()),
                result.coinBox().isCoinSavingEnabled(),
                result.coinBox().getCoinSavingStartDate(),
                result.account().getAccountOpenDate()
        );
    }
}
