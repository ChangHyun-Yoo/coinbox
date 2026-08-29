package com.kakaobank.coinbox.coinboxpolicy.repository;

import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 저금통 가입일에 적용할 상품 버전과 정책을 JPA Native Query로 조회한다.
 */
public interface CoinBoxPolicyQueryRepository extends Repository<CoinBoxPolicy, Long> {

    /**
     * 가입일에 유효한 최신 저금통 상품 버전과 한도 정책을 조회한다.
     */
    @Query(value = """
            select
                pv.product_version_id,
                cp.coinbox_policy_id,
                cp.max_amount
            from product p
            join product_version pv
              on pv.product_id = p.product_id
            join coinbox_policy cp
              on cp.product_version_id = pv.product_version_id
            where p.product_type = 'COINBOX'
              and pv.effective_from <= :effectiveDate
              and :effectiveDate < pv.effective_to
            order by pv.effective_from desc, pv.version_number desc
            limit 1
            """, nativeQuery = true)
    Optional<CoinBoxPolicyProjection> findEffectivePolicyProjection(
            @Param("effectiveDate") LocalDate effectiveDate
    );

    /**
     * 기존 계약이 고정한 상품 버전의 한도 정책을 조회한다.
     */
    @Query(value = """
            select
                pv.product_version_id,
                cp.coinbox_policy_id,
                cp.max_amount
            from product_version pv
            join coinbox_policy cp
              on cp.product_version_id = pv.product_version_id
            where pv.product_version_id = :productVersionId
            """, nativeQuery = true)
    Optional<CoinBoxPolicyProjection> findPolicyProjectionByProductVersionId(
            @Param("productVersionId") Long productVersionId
    );

    /**
     * 조회 기술에 의존하는 프로젝션을 가입 서비스가 사용하는 불변 읽기 모델로 변환한다.
     */
    default Optional<CoinBoxPolicySnapshot> findEffectivePolicy(LocalDate effectiveDate) {
        return toSnapshot(findEffectivePolicyProjection(effectiveDate));
    }

    /**
     * 상품 버전 기준 정책 프로젝션을 서비스용 읽기 모델로 반환한다.
     */
    default Optional<CoinBoxPolicySnapshot> findByProductVersionId(Long productVersionId) {
        return toSnapshot(findPolicyProjectionByProductVersionId(productVersionId));
    }

    /**
     * Repository 내부 프로젝션을 계층 밖에서 사용할 불변 값으로 변환한다.
     */
    private Optional<CoinBoxPolicySnapshot> toSnapshot(Optional<CoinBoxPolicyProjection> projection) {
        return projection.map(policy -> new CoinBoxPolicySnapshot(
                policy.getProductVersionId(),
                policy.getCoinboxPolicyId(),
                policy.getMaxAmount()
        ));
    }

    /**
     * Native Query 결과의 정책 식별자와 한도를 타입 안전하게 제공한다.
     */
    interface CoinBoxPolicyProjection {

        Long getProductVersionId();

        /**
         * Spring Data가 coinbox_policy_id를 자동 변환할 수 있도록 DB 식별자 단위에 맞춘 접근자다.
         */
        Long getCoinboxPolicyId();

        Long getMaxAmount();
    }
}
