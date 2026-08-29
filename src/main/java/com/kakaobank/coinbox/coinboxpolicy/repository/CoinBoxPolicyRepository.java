package com.kakaobank.coinbox.coinboxpolicy.repository;

import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 상품 버전에 연결된 저금통 한도 정책을 저장하고 조회한다.
 */
public interface CoinBoxPolicyRepository extends JpaRepository<CoinBoxPolicy, Long> {

    /**
     * 계약이 참조하는 상품 버전의 저금통 정책을 조회한다.
     */
    Optional<CoinBoxPolicy> findByProductVersionId(Long productVersionId);
}
