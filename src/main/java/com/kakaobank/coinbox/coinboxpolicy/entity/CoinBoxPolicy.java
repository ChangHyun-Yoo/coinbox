package com.kakaobank.coinbox.coinboxpolicy.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저금통 상품 버전에 적용되는 최대 보유 한도를 관리한다.
 */
@Getter
@Entity
@Table(name = "COINBOX_POLICY")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinBoxPolicy extends BaseEntity {

    @Id
    @Column(name = "coinbox_policy_id", nullable = false, updatable = false)
    private Long coinBoxPolicyId;

    @Column(nullable = false, unique = true, updatable = false)
    private Long productVersionId;

    @Column(nullable = false, updatable = false)
    private Long maxAmount;

    /**
     * 양수인 최대 한도를 특정 상품 버전에 연결한 정책을 생성한다.
     */
    public static CoinBoxPolicy create(Long coinBoxPolicyId, Long productVersionId, Long maxAmount) {
        if (maxAmount == null || maxAmount <= 0L) {
            throw new IllegalArgumentException("maxAmount must be positive");
        }
        CoinBoxPolicy policy = new CoinBoxPolicy();
        policy.coinBoxPolicyId = coinBoxPolicyId;
        policy.productVersionId = productVersionId;
        policy.maxAmount = maxAmount;
        return policy;
    }
}
