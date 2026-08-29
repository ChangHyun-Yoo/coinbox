package com.kakaobank.coinbox.coinbox.response;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.product.entity.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저금통 가입 조건을 충족한 하나의 입출금계좌 응답이다.
 */
@Schema(description = "저금통 가입이 가능한 ACTIVE 입출금계좌")
public record EligibleAccountResponse(
        @Schema(description = "Snowflake 계좌 ID", example = "710000000000000001")
        String accountId,
        @Schema(description = "하이픈 없는 13자리 입출금계좌 번호", example = "3333123456789", pattern = "^3333[0-9]{9}$")
        String accountNumber,
        @Schema(description = "현재 상품 유형", example = "DEMAND_DEPOSIT")
        ProductType productType,
        @Schema(description = "현재 잔액(원)", example = "245670", minimum = "0")
        Long balance
) {

    /**
     * 가입 가능한 계좌 Entity를 외부 응답으로 변환한다.
     */
    public static EligibleAccountResponse from(Account account) {
        return new EligibleAccountResponse(
                String.valueOf(account.getAccountId()),
                account.getAccountNumber(),
                account.getProductType(),
                account.getBalance()
        );
    }
}
