package com.kakaobank.coinbox.product.repository;

import com.kakaobank.coinbox.product.entity.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, Long> {

    Optional<ProductVersion> findByProductIdAndVersionNumber(Long productId, Integer versionNumber);
}
