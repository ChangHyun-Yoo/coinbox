package com.kakaobank.coinbox.accountentry.entity;

import lombok.Getter;

@Getter
public enum EntryType {
    DEPOSIT("입금"),
    WITHDRAWAL("출금");

    private final String description;

    EntryType(String description) {
        this.description = description;
    }
}
