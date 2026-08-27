package com.kakaobank.coinbox.product.repository;

import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductType(ProductType productType);
}
