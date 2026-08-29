package com.kakaobank.coinbox.product.repository;

import com.kakaobank.coinbox.coinboxpolicy.entity.CoinBoxPolicy;
import com.kakaobank.coinbox.coinboxpolicy.repository.CoinBoxPolicyRepository;
import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.product.entity.ProductVersion;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("상품 Repository")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVersionRepository productVersionRepository;

    @Autowired
    private CoinBoxPolicyRepository coinBoxPolicyRepository;

    @Test
    @DisplayName("상품 유형은 한 번만 저장할 수 있다")
    void rejectsDuplicatedProductType() {
        // given: ID만 다른 같은 저금통 상품 유형을 준비한다.
        Product first = Product.create(100L, ProductType.COINBOX, "저금통");
        Product second = Product.create(101L, ProductType.COINBOX, "새 저금통");
        productRepository.saveAndFlush(first);

        // when & then: DB의 product_type UK가 두 번째 저장을 거부한다.
        assertThatThrownBy(() -> productRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 상품에서 버전 번호를 중복 저장할 수 없다")
    void rejectsDuplicatedVersionNumberInProduct() {
        // given: 같은 상품과 버전 번호를 갖는 상품 버전 두 개를 준비한다.
        ProductVersion first = productVersion(200L, 100L, 1);
        ProductVersion second = productVersion(201L, 100L, 1);
        productVersionRepository.saveAndFlush(first);

        // when & then: product_id와 version_number 복합 UK가 중복 저장을 거부한다.
        assertThatThrownBy(() -> productVersionRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("하나의 상품 버전에는 저금통 정책을 하나만 저장한다")
    void rejectsDuplicatedPolicyForProductVersion() {
        // given: 같은 상품 버전을 참조하는 정책 두 개를 준비한다.
        CoinBoxPolicy first = CoinBoxPolicy.create(300L, 200L, 100_000L);
        CoinBoxPolicy second = CoinBoxPolicy.create(301L, 200L, 200_000L);
        coinBoxPolicyRepository.saveAndFlush(first);

        // when & then: product_version_id UK가 두 번째 정책을 거부한다.
        assertThatThrownBy(() -> coinBoxPolicyRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상품 유형과 버전 번호 및 정책을 조회한다")
    void findsProductVersionAndPolicyByBusinessKeys() {
        // given: 저금통 상품, 첫 버전과 최대 한도 정책을 저장한다.
        productRepository.saveAndFlush(Product.create(110L, ProductType.COINBOX, "저금통"));
        productVersionRepository.saveAndFlush(productVersion(210L, 110L, 1));
        coinBoxPolicyRepository.saveAndFlush(CoinBoxPolicy.create(310L, 210L, 100_000L));

        // when & then: 각 업무 키를 이용해 연결할 데이터를 조회할 수 있다.
        assertThat(productRepository.findByProductType(ProductType.COINBOX)).isPresent();
        assertThat(productVersionRepository.findByProductIdAndVersionNumber(110L, 1)).isPresent();
        assertThat(coinBoxPolicyRepository.findByProductVersionId(210L))
                .isPresent()
                .get()
                .extracting(CoinBoxPolicy::getMaxAmount)
                .isEqualTo(100_000L);
    }

    private ProductVersion productVersion(Long versionId, Long productId, int versionNumber) {
        return ProductVersion.create(
                versionId,
                productId,
                versionNumber,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(9999, 12, 31)
        );
    }
}
