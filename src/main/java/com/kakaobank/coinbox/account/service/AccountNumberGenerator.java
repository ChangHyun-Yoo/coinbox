package com.kakaobank.coinbox.account.service;

import com.kakaobank.coinbox.account.repository.AccountRepository;
import com.kakaobank.coinbox.common.exception.BusinessException;
import com.kakaobank.coinbox.common.exception.ErrorCode;
import com.kakaobank.coinbox.product.entity.ProductType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 과제 범위의 상품별 계좌번호 규칙에 따라 13자리 계좌번호 후보를 생성한다.
 * 실제 금융권의 중앙 채번, 체크 디지트와 폐쇄 계좌번호 재사용 정책은 이 책임에 포함하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountNumberGenerator {

    static final int MAX_GENERATION_ATTEMPTS = 100;

    private static final int SUFFIX_BOUND = 1_000_000_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    /**
     * 상품 prefix와 9자리 난수를 조합하고, 이미 사용 중인 번호라면 새 후보를 만든다.
     * 조회 직후의 동시 저장 경쟁까지 완전히 막을 수는 없으므로 ACCOUNT의 유니크 제약을 최종 안전장치로 둔다.
     */
    public String generate(ProductType productType) {
        String prefix = resolvePrefix(productType);

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String suffix = String.format(Locale.ROOT, "%09d", SECURE_RANDOM.nextInt(SUFFIX_BOUND));
            String candidate = prefix + suffix;
            if (accountRepository.countByAccountNumber(candidate) == 0L) {
                return candidate;
            }
            log.debug("계좌번호 후보가 중복되어 재채번합니다. productType={}, attempt={}", productType, attempt + 1);
        }

        log.warn("계좌번호 생성 재시도 한도를 초과했습니다. productType={}, attempts={}",
                productType, MAX_GENERATION_ATTEMPTS);
        throw new BusinessException(ErrorCode.ACCOUNT_NUMBER_GENERATION_FAILED);
    }

    /**
     * 상품 유형을 과제에서 정의한 4자리 계좌번호 접두어로 변환한다.
     */
    private String resolvePrefix(ProductType productType) {
        if (productType == null) {
            throw new IllegalArgumentException("productType must not be null");
        }

        return switch (productType) {
            case DEMAND_DEPOSIT -> "3333";
            case COINBOX -> "3310";
            case MEETING_ACCOUNT -> "7979";
            case INSTALLMENT_SAVING, FIXED_DEPOSIT ->
                    throw new IllegalArgumentException("Account number prefix is not defined for " + productType);
        };
    }
}
