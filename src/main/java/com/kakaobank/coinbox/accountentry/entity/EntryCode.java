package com.kakaobank.coinbox.accountentry.entity;

import lombok.Getter;

@Getter
public enum EntryCode {
    COIN_SAVING("동전 모으기"),
    COINBOX("저금통"),
    COINBOX_EMPTY("비우기"),
    COINBOX_TERMINATION("저금통 해지");

    private final String description;

    EntryCode(String description) {
        this.description = description;
    }
}
