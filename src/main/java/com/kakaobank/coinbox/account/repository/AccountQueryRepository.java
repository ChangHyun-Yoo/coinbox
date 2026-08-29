package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.product.entity.ProductType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 계좌 목록 화면에 필요한 계좌와 상품 정보를 JPA Native Query 한 번으로 조회한다.
 */
public interface AccountQueryRepository extends Repository<Account, Long> {

    @Query(value = """
            select
                a.account_id,
                a.product_type,
                a.account_number,
                a.balance,
                p.product_name,
                a.parent_account_id
            from account a
            left join product p
              on p.product_type = a.product_type
            where a.customer_id = :customerId
              and a.account_status = 'ACTIVE'
            order by a.account_id
            """, nativeQuery = true)
    List<ActiveAccountProjection> findActiveAccountProjections(@Param("customerId") Long customerId);

    /**
     * Spring Data의 인터페이스 프로젝션을 서비스가 사용하는 불변 읽기 모델로 변환한다.
     */
    default List<ActiveAccountRow> findActiveAccounts(Long customerId) {
        return findActiveAccountProjections(customerId).stream()
                .map(row -> new ActiveAccountRow(
                        row.getAccountId(),
                        ProductType.valueOf(row.getProductType()),
                        row.getAccountNumber(),
                        row.getBalance(),
                        row.getProductName(),
                        row.getParentAccountId()
                ))
                .toList();
    }

    /**
     * Native Query 결과의 컬럼명을 타입 안전한 Java 접근자로 노출한다.
     */
    interface ActiveAccountProjection {

        Long getAccountId();

        String getProductType();

        String getAccountNumber();

        Long getBalance();

        String getProductName();

        Long getParentAccountId();
    }
}
