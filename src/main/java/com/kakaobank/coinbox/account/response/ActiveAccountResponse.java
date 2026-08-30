package com.kakaobank.coinbox.account.response;

import com.kakaobank.coinbox.account.repository.ActiveAccountRow;
import com.kakaobank.coinbox.product.entity.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 최상위 계좌와 연결된 활성 자식 계좌를 함께 표현하는 API 응답이다.
 */
@Schema(description = "최상위 ACTIVE 계좌와 1단계 자식 계좌")
public record ActiveAccountResponse(
        @Schema(description = "Snowflake 계좌 ID", example = "710000000000000001")
        String accountId,
        @Schema(
                description = "현재 상품 유형: DEMAND_DEPOSIT(입출금통장), "
                        + "BUSINESS_DEMAND_DEPOSIT(개인사업자통장), COINBOX(저금통), "
                        + "MEETING_ACCOUNT(모임통장), INSTALLMENT_SAVING(적금), FIXED_DEPOSIT(정기예금)",
                example = "DEMAND_DEPOSIT"
        )
        ProductType productType,
        @Schema(description = "하이픈 없는 13자리 계좌번호", example = "3333000000001", pattern = "^[0-9]{13}$")
        String accountNumber,
        @Schema(description = "현재 잔액(원)", example = "253400", minimum = "0")
        Long balance,
        @Schema(description = "상품명", example = "입출금통장")
        String productName,
        @Schema(description = "이 계좌를 근거계좌로 사용하는 ACTIVE 자식 계좌. 없으면 빈 배열")
        List<ChildAccountResponse> childAccount
) {

    /**
     * 조회 행과 이미 조립된 자식 목록을 외부 응답으로 변환한다.
     */
    public static ActiveAccountResponse of(ActiveAccountRow row, List<ChildAccountResponse> children) {
        return new ActiveAccountResponse(
                String.valueOf(row.accountId()),
                row.productType(),
                row.accountNumber(),
                row.balance(),
                row.productName(),
                List.copyOf(children)
        );
    }
}
