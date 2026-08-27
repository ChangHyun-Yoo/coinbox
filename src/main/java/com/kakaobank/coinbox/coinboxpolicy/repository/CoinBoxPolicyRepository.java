package com.kakaobank.coinbox.coinboxpolicy.repository;

import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoinBoxPolicyRepository extends JpaRepository<CoinBoxPolicy, Long> {

    Optional<CoinBoxPolicy> findByProductVersionId(Long productVersionId);
}
