package com.kakaobank.coinbox.coinboxpolicy.repository;

import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.product.entity.ProductVersion;
import com.kakaobank.coinbox.product.repository.ProductRepository;
import com.kakaobank.coinbox.product.repository.ProductVersionRepository;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("저금통 정책 Query Repository")
class CoinBoxPolicyQueryRepositoryTest {

    @Autowired
    private CoinBoxPolicyQueryRepository coinBoxPolicyQueryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVersionRepository productVersionRepository;

    @Autowired
    private CoinBoxPolicyRepository coinBoxPolicyRepository;

    @Test
    @DisplayName("가입일에 유효한 최신 저금통 상품 버전과 정책을 조회한다")
    void findsEffectiveCoinBoxPolicy() {
        // given: 서로 다른 적용 기간의 저금통 상품 버전과 정책이 있다.
        productRepository.saveAndFlush(Product.create(1L, ProductType.COINBOX, "저금통"));
        productVersionRepository.saveAndFlush(ProductVersion.create(
                10L, 1L, 1, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)
        ));
        productVersionRepository.saveAndFlush(ProductVersion.create(
                11L, 1L, 2, LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)
        ));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(20L, 10L, 50_000L));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(21L, 11L, 100_000L));

        // when: 2026년 가입일의 정책을 조회한다.
        CoinBoxPolicySnapshot policy = coinBoxPolicyQueryRepository
                .findEffectivePolicy(LocalDate.of(2026, 8, 28))
                .orElseThrow();

        // then: 최신 유효 버전의 정책을 반환한다.
        assertThat(policy)
                .returns(11L, CoinBoxPolicySnapshot::productVersionId)
                .returns(21L, CoinBoxPolicySnapshot::coinBoxPolicyId)
                .returns(100_000L, CoinBoxPolicySnapshot::maxAmount);
    }

    @Test
    @DisplayName("계약에 저장된 상품 버전 ID로 저금통 정책을 조회한다")
    void findsPolicyByContractProductVersionId() {
        // given: 계약이 참조할 저금통 상품 버전과 정책을 저장한다.
        productRepository.saveAndFlush(Product.create(1L, ProductType.COINBOX, "저금통"));
        productVersionRepository.saveAndFlush(ProductVersion.create(
                11L, 1L, 2, LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)
        ));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(21L, 11L, 100_000L));

        // when: 계약의 product_version_id로 정책을 조회한다.
        CoinBoxPolicySnapshot policy = coinBoxPolicyQueryRepository.findByProductVersionId(11L).orElseThrow();

        // then: 날짜 재계산 없이 계약 당시 정책을 반환한다.
        assertThat(policy)
                .returns(11L, CoinBoxPolicySnapshot::productVersionId)
                .returns(21L, CoinBoxPolicySnapshot::coinBoxPolicyId)
                .returns(100_000L, CoinBoxPolicySnapshot::maxAmount);
    }
}
