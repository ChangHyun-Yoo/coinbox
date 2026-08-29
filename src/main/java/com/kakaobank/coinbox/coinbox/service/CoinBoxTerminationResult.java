package com.kakaobank.coinbox.coinbox.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.accountcontract.entity.AccountContract;

import java.time.LocalDate;

/**
 * 저금통 해지 후 계좌·계약 상태와 이전된 잔액을 묶는다.
 */
public record CoinBoxTerminationResult(
        Account account,
        AccountContract accountContract,
        Long transferredAmount,
        LocalDate terminationDate
) {
}
