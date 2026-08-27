package com.kakaobank.coinbox.product.entity;

import lombok.Getter;

/**
 * 계좌의 현재 운영 유형과 상품군을 식별하는 변경되지 않는 업무 값이다.
 */
@Getter
public enum ProductType {
    DEMAND_DEPOSIT("입출금통장"),
    COINBOX("저금통"),
    MEETING_ACCOUNT("모임통장"),
    INSTALLMENT_SAVING("적금"),
    FIXED_DEPOSIT("정기예금");

    private final String description;

    ProductType(String description) {
        this.description = description;
    }
}
