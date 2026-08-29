package com.kakaobank.coinbox.financialtransaction.service;

import com.kakaobank.coinbox.accountentry.entity.EntryCode;

/**
 * 공통 이체 로직이 업무별 원장 코드와 통장 적요를 알 수 있도록 전달하는 불변 명세다.
 */
public record TransferLedgerSpec(
        EntryCode sourceEntryCode,
        String sourceDescription,
        EntryCode targetEntryCode,
        String targetDescription
) {

    private static final int DESCRIPTION_MAX_LENGTH = 50;

    public TransferLedgerSpec {
        if (sourceEntryCode == null || targetEntryCode == null) {
            throw new IllegalArgumentException("entryCode must not be null");
        }
        validateDescription(sourceDescription, "sourceDescription");
        validateDescription(targetDescription, "targetDescription");
    }

    /**
     * 통장 적요가 비어 있지 않고 DB 컬럼 길이 안에 있는지 확인한다.
     */
    private static void validateDescription(String description, String fieldName) {
        if (description == null || description.isBlank() || description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 50 characters");
        }
    }
}
