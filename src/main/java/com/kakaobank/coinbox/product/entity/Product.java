package com.kakaobank.coinbox.product.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예금·적금·저금통처럼 서로 다른 계약과 정책을 갖는 상품군의 기준 정보다.
 */
@Getter
@Entity
@Table(name = "PRODUCT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, updatable = false)
    private ProductType productType;

    @Column(nullable = false, length = 100)
    private String productName;

    /**
     * 고유한 상품 유형과 사용자 표시명을 가진 상품군을 생성한다.
     */
    public static Product create(Long productId, ProductType productType, String productName) {
        if (productType == null) {
            throw new IllegalArgumentException("productType must not be null");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        Product product = new Product();
        product.productId = productId;
        product.productType = productType;
        product.productName = productName;
        return product;
    }
}
