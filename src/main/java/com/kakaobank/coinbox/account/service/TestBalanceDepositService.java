package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.account.response.TestBalanceDepositResponse;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.product.entity.ProductType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 테스트를 위해 거래·원장 처리 없이 특정 계좌의 잔고만 증가시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestBalanceDepositService {

    private final AccountRepository accountRepository;

    /**
     * 별도 상태 검증이나 잠금 없이 계좌번호와 금액 조건으로 단순 잔고 UPDATE를 실행한다.
     */
    @Transactional
    public TestBalanceDepositResponse deposit(String accountNumber, Long amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (account.getProductType() != ProductType.DEMAND_DEPOSIT) {
            throw new BusinessException(ErrorCode.TEST_BALANCE_DEPOSIT_NOT_ALLOWED);
        }

        int updatedCount = accountRepository.increaseBalance(accountNumber, amount);
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        Account updatedAccount = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        log.debug("테스트 전용 계좌 잔고 증가를 완료했습니다. accountId={}", updatedAccount.getAccountId());
        return new TestBalanceDepositResponse(accountNumber, amount, updatedAccount.getBalance());
    }
}
