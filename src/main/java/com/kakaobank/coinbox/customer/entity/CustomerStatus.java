package com.kakaobank.coinbox.customer.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 개인 고객의 서비스 이용 가능 여부와 탈퇴 상태를 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum CustomerStatus {
    ACTIVE("정상"),
    RESTRICTED("이용 제한"),
    WITHDRAWN("탈퇴");

    private final String description;
}
