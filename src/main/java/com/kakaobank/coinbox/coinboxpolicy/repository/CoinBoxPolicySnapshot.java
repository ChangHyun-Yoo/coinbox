package com.kakaobank.coinbox.coinboxpolicy.repository;

/**
 * 가입일에 유효한 저금통 상품 버전과 정책을 함께 반환하는 읽기 모델이다.
 */
public record CoinBoxPolicySnapshot(
        Long productVersionId,
        Long coinBoxPolicyId,
        Long maxAmount
) {
}
