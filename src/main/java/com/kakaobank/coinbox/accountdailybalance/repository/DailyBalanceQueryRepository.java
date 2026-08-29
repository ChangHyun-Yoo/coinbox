package com.kakaobank.coinbox.accountdailybalance.repository;

import com.kakaobank.coinbox.account.entity.Account;
import com.kakaobank.coinbox.accountdailybalance.batch.AccountBalanceSnapshot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 일별 잔액 대상은 온라인 조회와 같은 원칙에 따라 JPA Native Query로 읽는다.
 */
public interface DailyBalanceQueryRepository extends Repository<Account, Long> {

    @Query(value = """
            select
                a.account_id,
                a.balance
            from account a
            where a.account_status in ('ACTIVE', 'RESTRICTED')
            order by a.account_id
            """, nativeQuery = true)
    List<AccountBalanceProjection> findSnapshotTargetProjections();

    default List<AccountBalanceSnapshot> findSnapshotTargets() {
        return findSnapshotTargetProjections().stream()
                .map(row -> new AccountBalanceSnapshot(row.getAccountId(), row.getBalance()))
                .toList();
    }

    interface AccountBalanceProjection {

        Long getAccountId();

        Long getBalance();
    }
}
