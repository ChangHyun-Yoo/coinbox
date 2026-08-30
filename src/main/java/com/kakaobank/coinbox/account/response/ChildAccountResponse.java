package com.kakaobank.coinbox.account.response;

import com.kakaobank.coinbox.account.repository.ActiveAccountRow;
import com.kakaobank.coinbox.product.entity.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 최상위 계좌에 연결된 한 개의 활성 자식 계좌 응답이다.
 */
@Schema(description = "최상위 계좌에 연결된 ACTIVE 자식 계좌")
public record ChildAccountResponse(
        @Schema(description = "Snowflake 계좌 ID", example = "710000000000000002")
        String accountId,
        @Schema(description = "현재 상품 유형", example = "COINBOX")
        ProductType productType,
        @Schema(description = "하이픈 없는 13자리 계좌번호", example = "3310000000001", pattern = "^[0-9]{13}$")
        String accountNumber,
        @Schema(description = "현재 잔액(원)", example = "48730", minimum = "0")
        Long balance,
        @Schema(description = "상품명", example = "저금통")
        String productName
) {

    /**
     * Native Query 조회 행을 자식 계좌 응답으로 변환한다.
     */
    public static ChildAccountResponse from(ActiveAccountRow row) {
        return new ChildAccountResponse(
                String.valueOf(row.accountId()),
                row.productType(),
                row.accountNumber(),
                row.balance(),
                row.productName()
        );
    }
}
