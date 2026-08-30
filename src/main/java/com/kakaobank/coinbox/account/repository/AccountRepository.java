package com.kakaobank.coinbox.account.repository;

import com.kakaobank.coinbox.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 계좌번호 후보가 이미 사용 중인지 확인한다. 최종 중복 방지는 account_number 유니크 제약이 담당한다.
     */
    @Query(value = """
            select count(a.account_id)
            from account a
            where a.account_number = :accountNumber
            """, nativeQuery = true)
    long countByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * 테스트 잔고 증가 후 변경된 계좌 정보를 확인할 때 사용한다.
     */
    @Query(value = """
            select a.*
            from account a
            where a.account_number = :accountNumber
            """, nativeQuery = true)
    Optional<Account> findByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * 로컬 테스트를 위해 금융거래와 원장을 생성하지 않고 지정 계좌의 잔고만 증가시킨다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update account a
            set a.balance = a.balance + :amount,
                a.updated_datetime = current_timestamp
            where a.account_number = :accountNumber
              and a.product_type = 'DEMAND_DEPOSIT'
            """, nativeQuery = true)
    int increaseBalance(
            @Param("accountNumber") String accountNumber,
            @Param("amount") Long amount
    );

    /**
     * 로컬 테스트를 위해 금융거래와 원장을 생성하지 않고 입출금계좌의 잔고만 감소시킨다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update account a
            set a.balance = a.balance - :amount,
                a.updated_datetime = current_timestamp
            where a.account_number = :accountNumber
              and a.product_type = 'DEMAND_DEPOSIT'
              and a.balance >= :amount
            """, nativeQuery = true)
    int decreaseBalance(
            @Param("accountNumber") String accountNumber,
            @Param("amount") Long amount
    );

    /**
     * 이체에 참여하는 계좌를 ID 오름차순으로 잠가 반대 방향 이체 사이의 교착 가능성을 낮춘다.
     */
    @Query(value = """
            select a.*
            from account a
            where a.account_id in (:accountIds)
            order by a.account_id
            for update
            """, nativeQuery = true)
    List<Account> findAllByIdForUpdateOrderByAccountId(@Param("accountIds") List<Long> accountIds);

    @Query(value = """
            select count(a.account_id)
            from account a
            where a.customer_id = :customerId
              and a.product_type = 'COINBOX'
              and a.account_status <> 'CLOSED'
            """, nativeQuery = true)
    long countNonClosedCoinBoxes(@Param("customerId") Long customerId);

    @Query(value = """
            select a.*
            from account a
            where a.customer_id = :customerId
              and a.product_type = 'DEMAND_DEPOSIT'
              and a.account_status = 'ACTIVE'
              and a.parent_account_id is null
            order by a.account_id
            """, nativeQuery = true)
    List<Account> findEligibleDemandDepositAccounts(@Param("customerId") Long customerId);

    @Query(value = """
            select a.*
            from account a
            where a.account_id = :accountId
            for update
            """, nativeQuery = true)
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

    @Query(value = """
            select a.*
            from account a
            where a.customer_id = :customerId
              and a.account_number = :accountNumber
            """, nativeQuery = true)
    Optional<Account> findByCustomerIdAndAccountNumber(
            @Param("customerId") Long customerId,
            @Param("accountNumber") String accountNumber
    );
}
