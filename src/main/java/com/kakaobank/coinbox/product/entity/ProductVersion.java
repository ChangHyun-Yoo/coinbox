package com.kakaobank.coinbox.product.entity;

import com.kakaobank.coinbox.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 계약 시점에 적용할 상품 조건을 버전과 유효기간으로 고정한다.
 */
@Getter
@Entity
@Table(
        name = "PRODUCT_VERSION",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_version_product_number",
                columnNames = {"product_id", "version_number"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVersion extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long productVersionId;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private LocalDate effectiveTo;

    public static ProductVersion create(
            Long productVersionId,
            Long productId,
            Integer versionNumber,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        if (versionNumber == null || versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        if (effectiveFrom == null || effectiveTo == null || !effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must be before effectiveTo");
        }
        ProductVersion productVersion = new ProductVersion();
        productVersion.productVersionId = productVersionId;
        productVersion.productId = productId;
        productVersion.versionNumber = versionNumber;
        productVersion.effectiveFrom = effectiveFrom;
        productVersion.effectiveTo = effectiveTo;
        return productVersion;
    }
}
