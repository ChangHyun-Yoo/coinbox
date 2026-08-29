package com.kakaobank.coinbox.product.repository;

import com.kakaobank.coinbox.product.entity.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 상품별 정책 버전과 적용 기간을 저장하고 조회한다.
 */
public interface ProductVersionRepository extends JpaRepository<ProductVersion, Long> {

    /**
     * 상품 안에서 유일한 버전 번호로 상품 버전을 조회한다.
     */
    Optional<ProductVersion> findByProductIdAndVersionNumber(Long productId, Integer versionNumber);
}
