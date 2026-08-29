package com.kakaobank.coinbox.accountcontract.repository;

import com.kakaobank.coinbox.accountcontract.entity.AccountContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 계좌 계약 저장과 최신 계약의 비관적 잠금 조회를 담당한다.
 */
public interface AccountContractRepository extends JpaRepository<AccountContract, Long> {

    /**
     * 계좌의 가장 최근 계약 한 건을 잠가 상태 변경 경쟁을 직렬화한다.
     */
    @Query(value = """
            select ac.*
            from account_contract ac
            where ac.account_id = :accountId
            order by ac.contract_start_date desc, ac.account_contract_id desc
            limit 1
            for update
            """, nativeQuery = true)
    Optional<AccountContract> findLatestByAccountIdForUpdate(@Param("accountId") Long accountId);
}
