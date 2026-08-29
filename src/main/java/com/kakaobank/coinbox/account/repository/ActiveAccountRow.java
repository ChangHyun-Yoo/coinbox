package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.product.entity.ProductType;

/**
 * ACTIVE 계좌와 상품명을 한 번에 조회하여 부모·자식 응답을 조립하기 위한 평면 읽기 모델이다.
 */
public record ActiveAccountRow(
        Long accountId,
        ProductType productType,
        String accountNumber,
        Long balance,
        String productName,
        Long parentAccountId
) {
}
