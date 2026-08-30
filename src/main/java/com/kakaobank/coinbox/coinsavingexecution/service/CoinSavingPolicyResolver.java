package com.kakaobank.coinbox.coinsavingexecution.service;

import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyQueryRepository;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 한 동전모으기 Step에서 상품 버전별 정책을 최초 한 번만 조회해 재사용한다.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class CoinSavingPolicyResolver {

    private final CoinBoxPolicyQueryRepository coinBoxPolicyQueryRepository;
    private final Map<Long, CoinBoxPolicySnapshot> cachedPolicies = new HashMap<>();

    /**
     * 상품 버전의 불변 정책 스냅샷을 Step 범위 캐시에서 반환한다.
     */
    public CoinBoxPolicySnapshot resolve(Long productVersionId) {
        if (productVersionId == null) {
            throw new IllegalStateException("CoinBox policy is missing");
        }
        return cachedPolicies.computeIfAbsent(productVersionId, this::loadPolicy);
    }

    /**
     * 캐시에 없는 상품 버전의 정책을 DB에서 조회한다.
     */
    private CoinBoxPolicySnapshot loadPolicy(Long productVersionId) {
        CoinBoxPolicySnapshot policy = coinBoxPolicyQueryRepository
                .findByProductVersionId(productVersionId)
                .orElseThrow(() -> new IllegalStateException("CoinBox policy is missing"));
        log.debug("동전모으기 Step 정책을 캐시에 적재했습니다. productVersionId={}", productVersionId);
        return policy;
    }
}
