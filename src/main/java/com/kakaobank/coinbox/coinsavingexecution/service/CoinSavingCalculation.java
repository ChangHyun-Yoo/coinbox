package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinsavingexecution.entity.CoinSavingReasonCode;

/**
 * 동전모으기 금액 계산 결과다. 사유 코드가 없을 때만 실제 이체가 가능하다.
 */
public record CoinSavingCalculation(
        Long savingAmount,
        CoinSavingReasonCode reasonCode
) {

    /**
     * 실제 이체할 양수 금액을 가진 계산 결과를 생성한다.
     */
    public static CoinSavingCalculation transferable(Long savingAmount) {
        return new CoinSavingCalculation(savingAmount, null);
    }

    /**
     * 자금 이동 없이 종료할 업무 사유를 가진 결과를 생성한다.
     */
    public static CoinSavingCalculation skipped(CoinSavingReasonCode reasonCode) {
        return new CoinSavingCalculation(0L, reasonCode);
    }

    /**
     * 사유 코드가 없어 실제 이체 가능한 결과인지 확인한다.
     */
    public boolean transferable() {
        return reasonCode == null;
    }
}
