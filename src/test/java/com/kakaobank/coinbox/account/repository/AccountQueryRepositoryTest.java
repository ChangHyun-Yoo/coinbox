package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.product.entity.Product;
import com.kakaobank.coinbox.product.entity.ProductType;
import com.kakaobank.coinbox.product.repository.ProductRepository;
import com.kakaobank.coinbox.support.MySqlTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(MySqlTestContainer.class)
@DisplayName("고객 ACTIVE 계좌 Query Repository")
class AccountQueryRepositoryTest {

    @Autowired
    private AccountQueryRepository accountQueryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("ACTIVE 계좌와 상품명을 account_id 오름차순으로 조회한다")
    void findsActiveAccountsWithProductName() {
        // given: ACTIVE 부모·자식 계좌와 CLOSED 계좌를 준비한다.
        productRepository.saveAllAndFlush(List.of(
                Product.create(1L, ProductType.DEMAND_DEPOSIT, "입출금통장"),
                Product.create(2L, ProductType.COINBOX, "저금통")
        ));
        Account parent = account(20L, ProductType.DEMAND_DEPOSIT, "3333000000020", null, 100_000L);
        Account child = account(21L, ProductType.COINBOX, "3310000000021", 20L, 4_360L);
        Account closed = account(22L, ProductType.COINBOX, "3310000000022", 20L, 0L);
        closed.close();
        accountRepository.saveAllAndFlush(List.of(child, closed, parent));

        // when: 고객의 ACTIVE 계좌를 조회한다.
        List<ActiveAccountRow> rows = accountQueryRepository.findActiveAccounts(1L);

        // then: CLOSED 계좌를 제외하고 상품명과 부모 ID를 함께 반환한다.
        assertThat(rows)
                .extracting(ActiveAccountRow::accountId)
                .containsExactly(20L, 21L);
        assertThat(rows.getFirst())
                .returns("입출금통장", ActiveAccountRow::productName)
                .returns(null, ActiveAccountRow::parentAccountId);
        assertThat(rows.get(1))
                .returns("저금통", ActiveAccountRow::productName)
                .returns(20L, ActiveAccountRow::parentAccountId);
    }

    private Account account(
            Long accountId,
            ProductType productType,
            String accountNumber,
            Long parentAccountId,
            Long balance
    ) {
        return Account.create(
                accountId,
                1L,
                productType,
                accountNumber,
                parentAccountId,
                balance,
                LocalDate.of(2026, 8, 28)
        );
    }
}
