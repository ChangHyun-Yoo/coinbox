package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;

/**
 * 동전모으기 후보 처리 결과다. 처리 제외와 중복은 실행 이력을 만들지 않는다.
 */
public record CoinSavingResult(
        CoinSavingResultStatus status,
        Long savingAmount,
        Long transactionId,
        CoinSavingReasonCode reasonCode
) {

    /**
     * 완료된 이체 금액과 거래 ID를 가진 성공 결과를 생성한다.
     */
    public static CoinSavingResult success(Long amount, Long transactionId) {
        return new CoinSavingResult(CoinSavingResultStatus.SUCCESS, amount, transactionId, null);
    }

    /**
     * 실행 이력에 기록된 업무상 건너뜀 결과를 생성한다.
     */
    public static CoinSavingResult skipped(CoinSavingReasonCode reasonCode) {
        return new CoinSavingResult(CoinSavingResultStatus.SKIPPED, 0L, null, reasonCode);
    }

    /**
     * 설정 또는 실행일 조건에서 후보가 제외된 결과를 생성한다.
     */
    public static CoinSavingResult excluded() {
        return new CoinSavingResult(CoinSavingResultStatus.EXCLUDED, 0L, null, null);
    }

    /**
     * 같은 저금통과 실행일의 이력이 이미 존재하는 결과를 생성한다.
     */
    public static CoinSavingResult duplicate() {
        return new CoinSavingResult(CoinSavingResultStatus.DUPLICATE, 0L, null, null);
    }
}
