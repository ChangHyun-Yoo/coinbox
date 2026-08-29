package com.kakaobank.coinbox.coinbox.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.coinbox.entity.CoinBox;

/**
 * 같은 트랜잭션에서 생성한 저금통 계좌와 동전모으기 설정을 묶는다.
 */
public record CoinBoxOpenResult(
        Account account,
        CoinBox coinBox
) {
}
