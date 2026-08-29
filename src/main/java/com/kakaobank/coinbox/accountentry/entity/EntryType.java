package com.kakaobank.coinbox.accountentry.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계좌 관점에서 원장 금액이 입금인지 출금인지 나타낸다.
 */
@Getter
@RequiredArgsConstructor
public enum EntryType {
    DEPOSIT("입금"),
    WITHDRAWAL("출금");

    private final String description;
}
