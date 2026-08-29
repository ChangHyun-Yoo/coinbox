package com.kakaobank.coinbox.product.repository;

import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 상품군 기준 정보를 저장하고 상품 유형으로 조회한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 계좌의 비정규화된 상품 유형에 대응하는 상품 기준 정보를 조회한다.
     */
    Optional<Product> findByProductType(ProductType productType);
}
