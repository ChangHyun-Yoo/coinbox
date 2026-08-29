package com.kakaobank.coinbox.accountentry.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계좌 원장을 발생시킨 저금통 업무의 종류를 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum EntryCode {
    COIN_SAVING("동전 모으기"),
    COINBOX("저금통"),
    COINBOX_EMPTY("비우기"),
    COINBOX_TERMINATION("저금통 해지");

    private final String description;
}
